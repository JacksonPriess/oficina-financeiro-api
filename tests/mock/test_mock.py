import importlib.util
import json
import os
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
import uuid
from http.server import ThreadingHTTPServer
from pathlib import Path


class MockTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        os.environ.update(MOCK_DB=cls.temp.name + '/mock.db', PROVEDOR_CLIENT_SECRET='test-secret', MOCK_ADMIN_KEY='test-admin')
        spec = importlib.util.spec_from_file_location('mock_server', Path(__file__).resolve().parents[2] / 'mock/server.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        cls.module = module
        module.initialize()
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), module.Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = 'http://127.0.0.1:' + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()
        cls.temp.cleanup()

    def call(self, path, data=None, headers=None):
        req = urllib.request.Request(self.url + path, data=None if data is None else json.dumps(data).encode(),
                                     headers={'Content-Type': 'application/json', **(headers or {})})
        try:
            with urllib.request.urlopen(req, timeout=3) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def credentials(self):
        status, token = self.call('/oauth/token', {'client_id': 'oficina-financeiro', 'client_secret': 'test-secret'})
        self.assertEqual(200, status)
        return {'Authorization': 'Bearer ' + token['access_token'], 'Idempotency-Key': str(uuid.uuid4())}

    def payment(self, headers):
        data = {'referencia': str(uuid.uuid4()), 'osId': 1, 'valor': '125.50', 'moeda': 'BRL'}
        status, payment = self.call('/pagamentos', data, headers)
        self.assertEqual(200, status)
        return data, payment

    def test_chunked_json_from_streaming_client(self):
        import http.client
        connection = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        payload = json.dumps({'client_id': 'oficina-financeiro', 'client_secret': 'test-secret'}).encode()
        connection.request('POST', '/oauth/token', body=iter([payload[:15], payload[15:]]),
                           headers={'Content-Type': 'application/json'}, encode_chunked=True)
        response = connection.getresponse()
        body = json.loads(response.read())
        connection.close()
        self.assertEqual(200, response.status, body)
        self.assertIn('access_token', body)

    def test_openapi_expoe_autenticacao_e_operacoes(self):
        status, spec = self.call('/openapi.json')
        self.assertEqual(200, status)
        self.assertEqual([], spec['paths']['/oauth/token']['post']['security'])
        self.assertEqual([{'ProvedorBearer': []}], spec['paths']['/pagamentos']['post']['security'])
        self.assertEqual([{'MockAdminKey': []}], spec['paths']['/simulacao/pagamentos/{id}/aprovar']['post']['security'])
        for path in ['/swagger-ui.html', '/swagger-ui/swagger-ui-bundle.js', '/swagger-ui/swagger-ui.css']:
            with urllib.request.urlopen(self.url + path, timeout=3) as response:
                self.assertEqual(200, response.status)
                self.assertGreater(len(response.read()), 100)

    def test_authentication(self):
        self.assertEqual(401, self.call('/oauth/token', {'client_id': 'oficina-financeiro', 'client_secret': 'wrong'})[0])
        self.assertEqual(401, self.call('/pagamentos/anything')[0])

    def test_creation_idempotency_and_conflicting_payload(self):
        headers = self.credentials()
        data, payment = self.payment(headers)
        self.assertEqual(payment, self.call('/pagamentos', data, headers)[1])
        data['valor'] = '130.00'
        self.assertEqual(409, self.call('/pagamentos', data, headers)[0])

    def test_pending_approval_refund_and_duplicate_refund(self):
        headers = self.credentials()
        _, payment = self.payment(headers)
        path = '/pagamentos/' + payment['id']
        self.assertEqual('PENDENTE', payment['status'])
        self.assertEqual(409, self.call(path + '/estornos', {'valor': '125.50'}, headers)[0])
        admin = {'X-Mock-Admin-Key': 'test-admin'}
        self.assertEqual('APROVADO', self.call('/simulacao' + path + '/aprovar', {}, admin)[1]['status'])
        first = self.call(path + '/estornos', {'valor': '125.50'}, headers)
        self.assertEqual(200, first[0])
        self.assertEqual(first, self.call(path + '/estornos', {'valor': '125.50'}, headers))
        self.assertEqual(first[1]['id'], self.call(path, headers=headers)[1]['estornoId'])

    def test_approval_requires_simulation_key(self):
        self.assertEqual(401, self.call('/simulacao/pagamentos/x/aprovar', {})[0])

    def test_persistence_survives_initialization(self):
        headers = self.credentials()
        _, payment = self.payment(headers)
        self.module.initialize()
        self.assertEqual(payment['id'], self.call('/pagamentos/' + payment['id'], headers=headers)[1]['id'])

    def test_expired_token_rejected(self):
        headers = self.credentials()
        import sqlite3
        with sqlite3.connect(self.module.DB) as db:
            db.execute('UPDATE token SET expira=0 WHERE valor=?', (headers['Authorization'][7:],))
        self.assertEqual(401, self.call('/pagamentos/x', headers=headers)[0])


if __name__ == '__main__':
    unittest.main()
