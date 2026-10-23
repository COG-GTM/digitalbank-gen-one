# Digital Bank on Kubernetes - secrets

No credentials are stored in these manifests. Create the secrets in the `digibank`
namespace from your secret store before running `deploy-digibank.sh`:

```bash
kubectl -n digibank create secret generic digibank-secrets \
  --from-literal=bank-jwt-secret="$(openssl rand -base64 48)" \
  --from-literal=credit-jwt-secret="$(openssl rand -base64 48)" \
  --from-literal=bank-admin-password="<bank admin password>" \
  --from-literal=credit-admin-password="<credit admin password>" \
  --from-literal=keystore-password="<keystore password>" \
  --from-literal=artemis-user="<broker user>" \
  --from-literal=artemis-password="<broker password>" \
  --from-literal=obp-consumer-key="<open banking consumer key or empty>"

kubectl -n digibank create secret generic digibank-keystore \
  --from-file=digisic.p12=/path/to/digisic.p12
```

The keystore is mounted at `/usr/local/tomcat/conf/digisic.p12`; the bank and credit
services refuse to start without a JWT signing key.
