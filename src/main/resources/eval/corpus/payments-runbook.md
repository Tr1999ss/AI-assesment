# Payments Service Runbook

## Error codes
ERR-4012 means the payment gateway timed out before confirming the charge; the charge may still succeed.
ERR-4013 means the card was declined by the issuer and must not be retried automatically.
ERR-5003 means a ledger mismatch between the payments database and the accounting ledger.

## Retry policy
Gateway timeouts (ERR-4012) are retried up to three times with exponential backoff starting at two seconds.
Before each retry, the service checks the gateway for an existing charge to avoid charging twice.

## Escalation
If the error rate exceeds two percent for five minutes, page the on-call engineer through the PagerDuty
service PAY-ONCALL. Ledger mismatches (ERR-5003) are always escalated to the finance systems team.

## Rollback
Roll back a bad deployment with the deploy tool's rollback command for the payments-api service, then
confirm the error rate has recovered on the payments dashboard before closing the incident.
