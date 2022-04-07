#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# the one argument is the path to the confluent jms client fat jar on your local system

cd package/target
gunzip openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz 
mkdir -p openmessaging-benchmark-0.0.1-SNAPSHOT/lib 
cp -p ${1} openmessaging-benchmark-0.0.1-SNAPSHOT/lib/.
tar --append --file=openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar openmessaging-benchmark-0.0.1-SNAPSHOT/lib/kafka-jms-client-fat-6.2.1.jar
gzip openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar

