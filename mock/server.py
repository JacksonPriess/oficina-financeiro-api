"""Provedor didático HTTP. Não implementa o contrato oficial do Mercado Pago."""
import hmac
import json
import os
import secrets
import sqlite3
import time
import uuid
from pathlib import Path
from decimal import Decimal, InvalidOperation
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DB = os.environ.get('MOCK_DB', '/data/mock.db')
CLIENT_ID = os.environ.get('PROVEDOR_CLIENT_ID', 'oficina-financeiro')
CLIENT_SECRET = os.environ['PROVEDOR_CLIENT_SECRET']
ADMIN_KEY = os.environ['MOCK_ADMIN_KEY']


def initialize():
    os.makedirs(os.path.dirname(os.path.abspath(DB)), exist_ok=True)
    with sqlite3.connect(DB) as db:
        db.executescript('''
            CREATE TABLE IF NOT EXISTS token (valor TEXT PRIMARY KEY, expira INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS pagamento (
                id TEXT PRIMARY KEY, chave TEXT UNIQUE NOT NULL, referencia TEXT NOT NULL,
                os_id INTEGER NOT NULL, valor TEXT NOT NULL, moeda TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDENTE', estorno_id TEXT, estorno_chave TEXT UNIQUE);
        ''')


class HttpError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Não registra credenciais, tokens ou corpos de requisição.

    def do_GET(self):
        self.handle_request()

    def do_POST(self):
        self.handle_request()

    def handle_request(self):
        try:
            if self.command == 'GET' and self.serve_documentation():
                return
            with sqlite3.connect(DB, timeout=10) as db:
                db.row_factory = sqlite3.Row
                db.execute('BEGIN IMMEDIATE')
                result = self.dispatch(db)
            self.send_json(200, result)
        except HttpError as exc:
            self.send_json(exc.status, {'mensagem': exc.message})
        except (ValueError, KeyError, TypeError, InvalidOperation):
            self.send_json(400, {'mensagem': 'Solicitação inválida.'})
        except sqlite3.Error:
            self.send_json(503, {'mensagem': 'Persistência indisponível.'})

    def serve_documentation(self):
        routes = {
            '/swagger-ui.html': ('static/index.html', 'text/html; charset=utf-8'),
            '/swagger-ui/index.html': ('static/index.html', 'text/html; charset=utf-8'),
            '/swagger-ui/swagger-ui-bundle.js': ('static/swagger-ui-bundle.js', 'application/javascript'),
            '/swagger-ui/swagger-ui.css': ('static/swagger-ui.css', 'text/css'),
            '/openapi.json': ('openapi.json', 'application/json'),
        }
        resource = routes.get(self.path.split('?', 1)[0])
        if resource is None:
            return False
        content = (Path(__file__).resolve().parent / resource[0]).read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', resource[1])
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)
        return True

    def body(self):
        transfer = self.headers.get('Transfer-Encoding', '').lower().strip()
        if transfer:
            if transfer != 'chunked' or self.headers.get('Content-Length') is not None:
                raise HttpError(400, 'Enquadramento HTTP inválido.')
            payload = bytearray()
            while True:
                line = self.rfile.readline(128)
                if not line.endswith(b'\r\n'):
                    raise HttpError(400, 'Chunk inválido.')
                size = int(line.split(b';', 1)[0].strip(), 16)
                if size < 0 or len(payload) + size > 16384:
                    raise HttpError(413, 'Corpo muito grande.')
                if size == 0:
                    # Consome trailers com limite; o mock não utiliza seus valores.
                    total = 0
                    while True:
                        trailer = self.rfile.readline(1024)
                        total += len(trailer)
                        if not trailer.endswith(b'\r\n') or total > 4096:
                            raise HttpError(400, 'Trailer inválido.')
                        if trailer == b'\r\n':
                            break
                    break
                chunk = self.rfile.read(size)
                if len(chunk) != size or self.rfile.read(2) != b'\r\n':
                    raise HttpError(400, 'Chunk incompleto.')
                payload.extend(chunk)
        else:
            size = int(self.headers.get('Content-Length', '0'))
            if size < 0 or size > 16384:
                raise HttpError(413, 'Corpo muito grande.')
            payload = self.rfile.read(size)
            if len(payload) != size:
                raise HttpError(400, 'Corpo incompleto.')
        value = json.loads(payload or b'{}')
        if not isinstance(value, dict):
            raise ValueError()
        return value

    def dispatch(self, db):
        path = self.path.split('?', 1)[0]
        if path == '/health' and self.command == 'GET':
            return {'status': 'UP'}
        if path == '/oauth/token' and self.command == 'POST':
            data = self.body()
            if not hmac.compare_digest(str(data.get('client_id', '')), CLIENT_ID) or not hmac.compare_digest(str(data.get('client_secret', '')), CLIENT_SECRET):
                raise HttpError(401, 'Credenciais inválidas.')
            token = secrets.token_urlsafe(32)
            db.execute('DELETE FROM token WHERE expira <= ?', (int(time.time()),))
            db.execute('INSERT INTO token VALUES (?,?)', (token, int(time.time()) + 300))
            return {'access_token': token, 'token_type': 'Bearer', 'expires_in': 300}
        if path.startswith('/simulacao/'):
            if not hmac.compare_digest(self.headers.get('X-Mock-Admin-Key', ''), ADMIN_KEY):
                raise HttpError(401, 'Chave de simulação inválida.')
            parts = path.strip('/').split('/')
            if len(parts) != 4 or parts[1] != 'pagamentos' or parts[3] != 'aprovar' or self.command != 'POST':
                raise HttpError(404, 'Operação não encontrada.')
            row = self.find(db, parts[2])
            if row['estorno_id']:
                raise HttpError(409, 'Pagamento já estornado.')
            db.execute("UPDATE pagamento SET status='APROVADO' WHERE id=?", (row['id'],))
            return self.output(self.find(db, row['id']))
        authorization = self.headers.get('Authorization', '')
        token = authorization.removeprefix('Bearer ') if authorization.startswith('Bearer ') else ''
        if not db.execute('SELECT 1 FROM token WHERE valor=? AND expira>?', (token, int(time.time()))).fetchone():
            raise HttpError(401, 'Token inválido ou expirado.')
        if path == '/pagamentos' and self.command == 'POST':
            data = self.body()
            key = self.headers.get('Idempotency-Key', '')
            reference = str(uuid.UUID(data['referencia']))
            amount = Decimal(str(data['valor']))
            if not key or len(key) > 100 or not amount.is_finite() or amount <= 0 or amount != amount.quantize(Decimal('.01')) or data['moeda'] != 'BRL' or int(data['osId']) <= 0:
                raise ValueError()
            row = db.execute('SELECT * FROM pagamento WHERE chave=?', (key,)).fetchone()
            if row:
                if row['referencia'] != reference or Decimal(row['valor']) != amount or row['os_id'] != int(data['osId']) or row['moeda'] != data['moeda']:
                    raise HttpError(409, 'Chave já utilizada com outros dados.')
                return self.output(row)
            payment_id = str(uuid.uuid4())
            db.execute('INSERT INTO pagamento(id,chave,referencia,os_id,valor,moeda) VALUES (?,?,?,?,?,?)',
                       (payment_id, key, reference, int(data['osId']), str(amount), data['moeda']))
            return self.output(self.find(db, payment_id))
        parts = path.strip('/').split('/')
        if len(parts) >= 2 and parts[0] == 'pagamentos':
            row = self.find(db, parts[1])
            if len(parts) == 2 and self.command == 'GET':
                return self.output(row)
            if len(parts) == 3 and parts[2] == 'estornos' and self.command == 'POST':
                data = self.body()
                key = self.headers.get('Idempotency-Key', '')
                if not key or row['status'] != 'APROVADO' or Decimal(str(data['valor'])) != Decimal(row['valor']):
                    raise HttpError(409, 'Estorno exige pagamento aprovado e valor integral.')
                if row['estorno_id']:
                    if row['estorno_chave'] != key:
                        raise HttpError(409, 'Estorno já solicitado por outra chave.')
                    return {'id': row['estorno_id'], 'pagamentoId': row['id']}
                refund_id = str(uuid.uuid4())
                db.execute('UPDATE pagamento SET estorno_id=?, estorno_chave=? WHERE id=?', (refund_id, key, row['id']))
                return {'id': refund_id, 'pagamentoId': row['id']}
        raise HttpError(404, 'Operação não encontrada.')

    def find(self, db, payment_id):
        row = db.execute('SELECT * FROM pagamento WHERE id=?', (payment_id,)).fetchone()
        if row is None:
            raise HttpError(404, 'Pagamento não encontrado.')
        return row

    def output(self, row):
        return {'id': row['id'], 'referencia': row['referencia'], 'valor': row['valor'],
                'moeda': row['moeda'], 'status': row['status'], 'estornoId': row['estorno_id']}

    def send_json(self, status, data):
        payload = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == '__main__':
    initialize()
    ThreadingHTTPServer(('0.0.0.0', int(os.environ.get('PORT', '8090'))), Handler).serve_forever()
