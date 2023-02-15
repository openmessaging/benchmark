#!/usr/bin/env bash
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

# pulsar package information
export PULSARPACKAGE={{ pulsar_package['url'] }}

export BROKERCONF={{ pulsar_package['broker'] }}

# pulsar connectivity
export SERVICEURL={% if tls is defined and tls|bool %}{{ serviceTlsUrl|trim }}{% else %}{{ serviceUrl|trim }}{% endif %}

export HTTPURL={% if tls is defined and tls|bool %}{{ httpsUrl|trim }}{% else %}{{ httpUrl|trim }}{% endif %}

export TLSCERTPATH={% if tls is defined and tls|bool %}/opt/benchmark/rootca/ca.cert.pem{% endif %}

# kafka connectivity
export BOOTSTRAPSERVERS={{ bootstrapServers }}
