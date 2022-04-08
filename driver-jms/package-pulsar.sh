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

cd package/target
tar zxvf openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
cd openmessaging-benchmark-0.0.1-SNAPSHOT
rm lib/org.apache.pulsar-*
rm lib/*jackson*
curl https://repo1.maven.org/maven2/com/datastax/oss/pulsar-jms-all/1.2.2/pulsar-jms-all-1.2.2.jar -o lib/pulsar-jms-all-1.2.2.jar
cd ..
rm openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
tar zcvf openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz openmessaging-benchmark-0.0.1-SNAPSHOT

