terraform init -input=false -no-color
terraform apply -input=false -auto-approve -no-color
echo Wait
sleep 15
echo "Setup TLS"
export IPADDRESSES=`cat terraform.tfstate | jello -rsml | grep \\\.private_ip - | awk 'BEGIN {ips = "";}{ gsub(/\"/,"",$3); gsub(/\;/,"",$3); if (ips != "") ips = ips","; ips = ips$3 }END{print ips;}' -`
./omb_tls_certificates.sh -c local -b $IPADDRESSES
echo Deploy - Playbook
ansible-playbook --user ec2-user --inventory `which terraform-inventory` --extra-vars "@vars.yaml" deploy.yaml
