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

BOOKIEPID=`ps -ef | grep pulsar/conf/bookkeeper | grep root | awk '{ print $2}' -`
BROKERPID=`ps -ef | grep pulsar/conf/broker | grep root | awk '{ print $2}' -`
cd ~
mkdir debug
cd debug
LOGDIR=`date -u "+%Y-%m-%d-%H"`
mkdir $LOGDIR
cd $LOGDIR
LOGFILE=`date -u "+%H-%M-%S"`
sudo jcmd ${BOOKIEPID} JFR.start name=bookie filename=~/debug/${LOGDIR}/bookie-${LOGFILE}.jfr
sudo jcmd ${BROKERPID} JFR.start name=broker filename=~/debug/${LOGDIR}/broker-${LOGFILE}.jfr settings=profile
sudo chmod 644 bookie-${LOGFILE}.*
sudo chmod 644 broker-${LOGFILE}.*

