# Deploy do Financeiro no EKS

O workflow [CI/CD Financeiro](../.github/workflows/ci.yaml) executa os testes Java com PostgreSQL e os testes HTTP do mock. Após os testes, um push na `main` ou uma execução manual nessa branch publica as duas imagens no Amazon Elastic Container Registry (ECR) e implanta os componentes no Amazon Elastic Kubernetes Service (EKS).

| Componente | Repositório ECR | Service interno |
| --- | --- | --- |
| API financeira | `oficina-financeiro-dev-repo` | `financeiro-api:8082` |
| Provedor mock | `oficina-provedor-mock-dev-repo` | `provedor-mock:8090` |

Cada execução usa uma tag formada pelo SHA do código, pelo identificador da execução e pelo número da tentativa. O workflow substitui as imagens nos manifestos com Kustomize e aguarda o rollout dos dois Deployments.

## Pré-requisitos

- Cluster `oficina-api-dev-cluster` em `us-east-1`, com nós disponíveis.
- Repositórios ECR provisionados por `oficina-infra-k8s`.
- RabbitMQ pronto no namespace `default`, com Service `rabbitmq` e Secret `rabbitmq-secret`.
- Banco `oficina_financeiro_db` e Secret `financeiro-database-secret` provisionados por `oficina-infra-db`.
- Driver Amazon EBS CSI e StorageClass `rabbitmq-gp3` disponíveis.

## Secrets do GitHub Actions

Configure os **Repository secrets** abaixo no repositório `oficina-financeiro-api`.

| Secret | Uso |
| --- | --- |
| `AWS_ACCESS_KEY_ID` | Credencial AWS do laboratório |
| `AWS_SECRET_ACCESS_KEY` | Credencial AWS do laboratório |
| `AWS_SESSION_TOKEN` | Token da sessão temporária AWS |
| `FINANCEIRO_API_KEY` | Header `X-API-Key` da API financeira |
| `PROVEDOR_CLIENT_SECRET` | Credencial compartilhada entre a API e o mock para emissão do token |
| `MOCK_ADMIN_KEY` | Header `X-Mock-Admin-Key` para aprovar pagamentos simulados |

O workflow cria `financeiro-api-secret` e `provedor-mock-secret` no Kubernetes. A chave administrativa do mock é injetada somente no mock. Os Secrets de banco e do RabbitMQ vêm dos workflows de infraestrutura. O Flyway da API aplica as migrations do financeiro ao iniciar.

## Recursos e persistência

Os manifestos estão em [k8s](../k8s). A API usa o perfil `rabbit`, acessa o mock por `http://provedor-mock:8090` e compartilha o broker `rabbitmq:5672` com o núcleo e o catálogo.

Os dois Services são do tipo `ClusterIP`, com acesso pela rede interna do cluster. As verificações da API usam o Actuator com API key. A verificação de prontidão da API inclui o PostgreSQL. O mock usa `/health`.

O mock monta o PVC (PersistentVolumeClaim, solicitação de volume persistente) `provedor-mock-data` em `/data`. Ele usa um volume EBS gp3 de 1 GiB para persistir cobranças e chaves de idempotência. O Deployment tem uma réplica e usa a estratégia `Recreate`, que encerra o Pod anterior antes de iniciar o novo. Isso mantém um único processo usando o arquivo SQLite e implica uma breve indisponibilidade durante a atualização. Preserve o PVC para manter os registros já associados aos identificadores de cobrança do financeiro.

## Verificação após o deploy

```bash
kubectl -n default get deploy,pods,pvc -l app.kubernetes.io/part-of=oficina-financeiro
kubectl -n default get svc financeiro-api provedor-mock
kubectl -n default logs deployment/financeiro-api --tail=100
kubectl -n default logs deployment/provedor-mock --tail=100
```

O resultado esperado é um Pod pronto para cada Deployment e o PVC em `Bound`. Para abrir as interfaces Swagger, execute os encaminhamentos em terminais separados.

```bash
kubectl -n default port-forward svc/financeiro-api 8082:8082
kubectl -n default port-forward svc/provedor-mock 8090:8090
```

- [Swagger da API financeira](http://localhost:8082/swagger-ui/index.html)
- [Swagger do mock](http://localhost:8090/swagger-ui.html)

A verificação ponta a ponta requer o núcleo com a Saga de pagamento implantada. Finalize uma OS no núcleo, acompanhe a criação da cobrança, aprove no mock e confirme que o núcleo libera a entrega. Em uma segunda OS aprovada, solicite o estorno pelo núcleo antes da entrega e confirme a compensação.
