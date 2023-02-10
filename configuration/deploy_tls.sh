#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

terraform init -input=false -no-color
terraform apply -input=false -auto-approve -no-color
echo Wait
sleep 15
echo "Setup TLS"
export IPADDRESSES=`cat terraform.tfstate | jello -rsml | grep \\\.private_ip - | awk 'BEGIN {ips = "";}{ gsub(/\"/,"",$3); gsub(/\;/,"",$3); if (ips != "") ips = ips","; ips = ips$3 }END{print ips;}' -`
./omb_tls_certificates.sh -c local -b $IPADDRESSES
echo Deploy - Playbook
ansible-playbook --user ec2-user --inventory `which terraform-inventory` --extra-vars "@vars.yaml" deploy.yaml
