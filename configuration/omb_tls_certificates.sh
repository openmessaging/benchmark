#! /bin/bash


#
# NOTE: this script is used for generating self signed ceritificates
#       to be used when Pulsar inTransit encryption is enabled
#
#


# Check if "openssl" executable is available
whichOpenssl=$(which openssl)
if [[ "${whichOpenssl}" == "" || "${whichOpenssl}" == *"not found"* ]]; then
  echo "Can't find \"openssl\" executable which is necessary to create TLS certificates"
  exit 10
fi

usage() {
   echo
   echo "Usage: omb_tls_certificates.sh [-h] -b <broker_host_list> \
                                        -c <pulsar_cluster_name> \
                                        [-root_pwd <rootPasswd>] \
                                        [-brkr_pwd <brkrPasswd>] \
                                        [-root_expr_days <rootCertExpDays>] \
                                        [-brkr_expr_days <brokre_cert_expire_days>] \
                                        [-certSubjLineStr <certificate_subject_line_string>]"
   echo "       -h   : show usage info"
   echo "       -b <broker_host_list> : broker hostname or IP list string (comma separated)"
   echo "       -c <pulsar_cluster_name> : Pulsar cluster name"
   echo "       [-root_pwd <rootPasswd> : the password of the self-signed root CA key]"
   echo "       [-brkr_pwd <brkrPasswd> : the password of the (broker) server key]"
   echo "       [-root_expr_days <rootCertExpDays>] : the expiration days of the self-signed root CA certificate (default 10 years)"
   echo "       [-brkr_expr_days <brokre_cert_expire_days>] : the expiration days of the signed (broker) server certificate (default 1 year)"
   echo "       [-certSubjLineStr <certificate_subject_line_string>] : the subject line string of the certificate"
   echo
}

if [[ $# -eq 0 || $# -gt 15 ]]; then
   usage
   exit 20
fi

rootCertExpDays=3650
brkrCertExpDays=365
certSubjLineStr="/C=US/ST=TX/L=Dallas/O=mytest.com"

forceDownload=0
brokerHostListStr=""
pulsarClusterName=""
rootPasswd="MyRootCACertPass"
brkrPasswd="MyBrkrSrvCertPass"
while [[ "$#" -gt 0 ]]; do
   case $1 in
      -h) usage; exit 0 ;;
      -d) forceDownload=1; ;;
      -b) brokerHostListStr="$2"; shift ;;
      -c) pulsarClusterName="$2"; shift ;;
      -root_pwd) rootPasswd="$2"; shift ;;
      -brkr_pwd) brkrPasswd="$2"; shift ;;
      -root_expr_days) rootCertExpDays="$2"; shift;;
      -brkr_expr_days) brkrCertExpDays="$2"; shift;;
      -certSubjLineStr) certSubjLineStr="$2"; shift;;
      *) echo "Unknown parameter passed: $1"; exit 20 ;;
   esac
   shift
done

echo

if [[ "${brokerHostListStr}" == ""  ]]; then
  echo "[ERROR] Broker host list string (comma separated) can't be empty"
  exit 30
fi

if [[ "${pulsarClusterName}" == ""  ]]; then
  echo "[ERROR] Pulsar cluster name can't be empty"
  exit 40
fi

if [[ "${rootPasswd}" == ""  ]]; then
  echo "[ERROR] The password of the self-signed root CA key can't be empty"
  exit 50
fi

if [[ "${brkrPasswd}" == ""  ]]; then
  echo "[ERROR] The password of the (broker) server key can't be empty"
  exit 60
fi

re='^[0-9]+$'
if [[ "${rootCertExpDays}" == "" || ! rootCertExpDays =~ $re ]]; then
  echo "[WARN] The expiration days of the self-signed root CA certificate is invalid. Use the default setting of 3650 days"
  rootCertExpDays=3650
fi

if [[ "${brkrCertExpDays}" == "" || ! brkrCertExpDays =~ $re ]]; then
  echo "[WARN] The expiration days of the signed (broker) server certificate is invalid. Use the default setting of 365 days"
  brkrCertExpDays=365
fi

if [[ "${certSubjLineStr}" == ""  ]]; then
  echo "[WARN] The subject line in the certificate is empty. Use a default string."
  certSubjLineStr="/C=US/ST=TX/L=Dallas/O=mytest.com"
fi

# always clean
rm -rf files
mkdir -p files
cd files

export CA_HOME=$(pwd)
echo ${CA_HOME}

mkdir -p private certs crl newcerts brokers
chmod 700 private/
touch index.txt index.txt.attr
echo 1000 > serial

stepCnt=0

# Check if openssl.cnf file exists locally. If not, download it
if [[ ! -f "../openssl.cnf" ]]; then
    echo "openssl.cnf file is missing"
    exit 70
else
    cp ../openssl.cnf .
fi

stepCnt=$((stepCnt+1))

# NOTE: the self signed root ca key and certificate names must be as below
ROOT_CA_KEY_NAME="ca.key.pem"
ROOT_CA_CERT_NAME="ca.cert.pem"

echo "== STEP ${stepCnt} :: Create a root key and a X.509 certificate for self-signing purpose =="
echo "   >> (${stepCnt}.1) Generate the self-signed root CA private key file"
$whichOpenssl genrsa -aes256 \
        -passout pass:${rootPasswd} \
        -out ${CA_HOME}/private/${ROOT_CA_KEY_NAME} \
        4096
chmod 400 ${CA_HOME}/private/${ROOT_CA_KEY_NAME}

echo "   >> (${stepCnt}.2) Generate the self-signed root CA certificate file"
$whichOpenssl req -config openssl.cnf \
        -new -x509 -sha256 \
        -extensions v3_ca \
        -key ${CA_HOME}/private/${ROOT_CA_KEY_NAME} \
        -out ${CA_HOME}/certs/${ROOT_CA_CERT_NAME} \
        -days ${rootCertExpDays} \
        -subj ${certSubjLineStr} \
        -passin pass:${rootPasswd}
chmod 444 ${CA_HOME}/certs/${ROOT_CA_CERT_NAME}

ls -1 ${CA_HOME}/certs/

echo
stepCnt=$((stepCnt+1))
echo "== STEP ${stepCnt} :: Generate and sign the Broker Server certificate for all specified Pulsar hosts =="

for brokerHost in $(echo $brokerHostListStr | sed "s/,/ /g"); do
   echo "   [Host:  $brokerHost]"

   # replace '.' with '-' in the broker host name or IP
   brokerHost2=${brokerHost//./-}

   BROKER_KEY_NAME="${CA_HOME}/brokers/broker.${brokerHost2}.key.pem"
   BROKER_KEY_PK8_NAME="${CA_HOME}/brokers/broker.${brokerHost2}.key-pk8.pem"
   BROKER_CSR_NAME="${CA_HOME}/brokers/broker.${brokerHost2}.csr.pem"
   BROKER_CRT_NAME="${CA_HOME}/brokers/broker.${brokerHost2}.crt.pem"

   echo "   >> (${stepCnt}.1) Generate the Server Certificate private key file"
   $whichOpenssl genrsa \
            -passout pass:${brkrPasswd} \
            -out ${BROKER_KEY_NAME} \
            2048

   echo
   echo "   >> (${stepCnt}.2) Convert the private key file to PKCS8 format"
   $whichOpenssl pkcs8 \
            -topk8 -nocrypt \
            -inform PEM -outform PEM \
            -in ${BROKER_KEY_NAME} \
            -out ${BROKER_KEY_PK8_NAME}

   echo
   echo "   >> (${stepCnt}.3) Generate the CSR file"
   $whichOpenssl req \
            -config openssl.cnf \
            -new -sha256 \
            -key ${BROKER_KEY_NAME} \
            -out ${BROKER_CSR_NAME} \
            -subj "${certSubjLineStr}/CN=${brokerHost}" \
            -passin pass:${brkrPasswd}

   echo
   echo "   >> (${stepCnt}.4) Sign the CSR with the ROOT certificate"
   $whichOpenssl ca \
            -config openssl.cnf \
            -extensions server_cert \
            -notext -md sha256 -batch \
            -days ${brkrCertExpDays} \
            -in ${BROKER_CSR_NAME} \
            -out ${BROKER_CRT_NAME} \
            -passin pass:${rootPasswd}
   echo
   echo
done
ls -1 ${CA_HOME}/brokers

cd ..

exit 0
