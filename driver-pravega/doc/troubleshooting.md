# TROUBLESHOOTING

## Log into one of the hosts and check the logs

```
terraform-inventory -inventory
ssh -i ~/.ssh/pravega_aws ec2-user@`terraform output segmentstore_0_ssh_host`
journalctl -u pravega-segmentstore
```

## Ansible failed to parse /usr/local/bin/terraform-inventory with script plugin

```
export TF_STATE=./
```

