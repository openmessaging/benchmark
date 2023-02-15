#!/bin/sh
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

WORKERPID=`ps -ef | grep BenchmarkWorker | grep root | awk '{ print $2}' -`
cd ~
mkdir debug
cd debug
LOGDIR=`date -u "+%Y-%m-%d-%H"`
mkdir $LOGDIR
cd $LOGDIR
LOGFILE=`date -u "+%H-%M-%S"`
sudo jmap -dump:live,format=b,file=worker-${LOGFILE}.hprof ${WORKERPID}
sudo tar cvf worker-${LOGFILE}.hprof.tar worker-${LOGFILE}.hprof
sudo rm worker-${LOGFILE}.hprof
sudo jcmd ${WORKERPID} JFR.dump name=worker filename=~/debug/${LOGDIR}/worker-${LOGFILE}.jfr
sudo chmod 644 worker-${LOGFILE}.*

