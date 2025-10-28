#!/usr/bin/env bash
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

set -euo pipefail

# Generate benchmark payload files with controlled variability and headers
#
# Dependencies: jq

show_help() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Generate benchmark payload files with controlled variability and headers.

OPTIONS:
    -o, --output-dir DIR        Output directory for payload files (required)
    -n, --num-files NUM         Number of payload files to generate (required)
    -s, --size BYTES            Target size of each payload in bytes (required)
    -H, --headers HEADERS       Headers to add to each payload
                                Format: "header1=value1:percentage1,value2:percentage2;header2=value3:percentage3,value4:percentage4"
                                Example: "environment=production:70,staging:30;region=us-east:50,us-west:50"
                                Percentages for each header should sum to 100
    -e, --entropy INT           Entropy/variability level (0-100, default: 50)
                                0 = identical payloads
                                100 = maximum variability
    -h, --help                  Show this help message

EXAMPLES:
    # Generate 100 files with 1KB payloads and medium entropy
    $(basename "$0") -o payloads -n 100 -s 1024 -e 50

    # Generate with header distribution (70% production, 30% staging)
    $(basename "$0") -o payloads -n 100 -s 1024 -H "environment=production:70,staging:30" -e 50

    # Generate with multiple headers
    $(basename "$0") -o payloads -n 100 -s 1024 -H "environment=production:70,staging:30;region=us-east:50,us-west:50" -e 50

    # Generate with low variability (almost identical payloads)
    $(basename "$0") -o payloads -n 50 -s 512 -e 10

    # Generate with high variability
    $(basename "$0") -o payloads -n 50 -s 512 -e 90

EOF
}

# Default values
OUTPUT_DIR=""
NUM_FILES=""
SIZE=""
HEADERS=""
ENTROPY="50"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -o|--output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -n|--num-files)
            NUM_FILES="$2"
            shift 2
            ;;
        -s|--size)
            SIZE="$2"
            shift 2
            ;;
        -H|--headers)
            HEADERS="$2"
            shift 2
            ;;
        -e|--entropy)
            ENTROPY="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Error: Unknown option: $1" >&2
            show_help
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$OUTPUT_DIR" ]] || [[ -z "$NUM_FILES" ]] || [[ -z "$SIZE" ]]; then
    echo "Error: Missing required arguments" >&2
    show_help
    exit 1
fi

# Validate numeric arguments
if ! [[ "$NUM_FILES" =~ ^[0-9]+$ ]] || [[ "$NUM_FILES" -lt 1 ]]; then
    echo "Error: Number of files must be a positive integer" >&2
    exit 1
fi

if ! [[ "$SIZE" =~ ^[0-9]+$ ]] || [[ "$SIZE" -lt 1 ]]; then
    echo "Error: Size must be a positive integer" >&2
    exit 1
fi

# Validate entropy
if ! [[ "$ENTROPY" =~ ^[0-9]+$ ]] || [[ "$ENTROPY" -lt 0 ]] || [[ "$ENTROPY" -gt 100 ]]; then
    echo "Error: Entropy must be an integer between 0 and 100" >&2
    exit 1
fi

# Check dependencies
if ! command -v jq &> /dev/null; then
    echo "Error: jq is required but not installed" >&2
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "Generating $NUM_FILES payload files in $OUTPUT_DIR"
echo "Target payload size: $SIZE bytes"
echo "Entropy: $ENTROPY"
if [[ -n "$HEADERS" ]]; then
    echo "Headers: $HEADERS"
fi
echo

# Parse headers into parallel arrays
# Format: "header1=value1:percentage1,value2:percentage2;header2=value3:percentage3,value4:percentage4"
declare -a HEADER_NAMES
declare -a HEADER_VALUE_SPECS

if [[ -n "$HEADERS" ]]; then
    IFS=';' read -ra HEADER_SPECS <<< "$HEADERS"
    for header_spec in "${HEADER_SPECS[@]}"; do
        IFS='=' read -ra HEADER_PARTS <<< "$header_spec"
        header_name="${HEADER_PARTS[0]}"
        values_spec="${HEADER_PARTS[1]}"
        HEADER_NAMES+=("$header_name")
        HEADER_VALUE_SPECS+=("$values_spec")
    done
fi

# Function to select weighted random value for a given header's value spec
select_header_value() {
    local values_spec="$1"

    declare -a value_list
    declare -a percentage_list

    IFS=',' read -ra PAIRS <<< "$values_spec"
    for pair in "${PAIRS[@]}"; do
        IFS=':' read -ra PARTS <<< "$pair"
        value="${PARTS[0]}"
        percentage="${PARTS[1]:-50}"
        value_list+=("$value")
        percentage_list+=("$percentage")
    done

    local rand=$((RANDOM % 100))
    local cumulative=0

    for i in "${!value_list[@]}"; do
        cumulative=$((cumulative + percentage_list[i]))

        if [[ $rand -lt $cumulative ]]; then
            echo "${value_list[$i]}"
            return
        fi
    done

    # Fallback
    echo "${value_list[0]}"
}

# Function to generate random string
random_string() {
    local length=$1
    cat /dev/urandom | LC_ALL=C tr -dc 'a-zA-Z0-9' | head -c "$length"
}

# Function to generate JSON payload
generate_json_payload() {
    local target_size=$1
    local entropy=$2  # Now 0-100
    local file_index=$3

    # Start with base fields
    local timestamp
    local id

    if [[ $entropy -gt 0 ]]; then
        timestamp=$((1000000000 + RANDOM * 10000))
    else
        timestamp=1234567890
    fi

    if [[ $entropy -gt 50 ]]; then
        id=$(random_string 16)
    else
        local id_variation=$((entropy * 10))
        id_variation=$((id_variation > 0 ? id_variation : 1))
        id="fixed-id-$((RANDOM % id_variation))"
    fi

    # Create base JSON
    local json=$(jq -n \
        --argjson ts "$timestamp" \
        --arg id "$id" \
        '{timestamp: $ts, id: $id}')

    # Calculate current size
    local current_size=$(echo -n "$json" | wc -c | tr -d ' ')

    if [[ $current_size -ge $target_size ]]; then
        echo "$json"
        return
    fi

    local remaining_size=$((target_size - current_size))

    # Determine number of fields based on entropy
    local num_fields
    if [[ $entropy -lt 30 ]]; then
        num_fields=1
    elif [[ $entropy -lt 70 ]]; then
        num_fields=$((1 + RANDOM % 3))
    else
        num_fields=$((2 + RANDOM % 4))
    fi

    # Calculate chars per field
    local chars_per_field=$((remaining_size / (num_fields + 1)))

    # Add data fields
    for ((i=0; i<num_fields; i++)); do
        local field_name
        if [[ $entropy -gt 50 ]]; then
            field_name="data_$i"
        else
            field_name="field_$i"
        fi

        local string_length=$chars_per_field
        if [[ $entropy -gt 50 ]]; then
            local variation=$((chars_per_field / 4))
            string_length=$((chars_per_field + (RANDOM % (variation * 2)) - variation))
            string_length=$((string_length > 1 ? string_length : 1))
        fi

        local field_value=$(random_string "$string_length")
        json=$(echo "$json" | jq --arg key "$field_name" --arg val "$field_value" '. + {($key): $val}')
    done

    # Fine-tune size with padding
    current_size=$(echo -n "$json" | wc -c | tr -d ' ')
    if [[ $current_size -lt $target_size ]]; then
        local padding_size=$((target_size - current_size - 15))
        if [[ $padding_size -gt 0 ]]; then
            local padding=$(random_string "$padding_size")
            json=$(echo "$json" | jq --arg pad "$padding" '. + {_padding: $pad}')
        fi
    fi

    echo "$json"
}

# Track sizes for statistics
declare -a SIZES

# Generate payload files
for ((i=0; i<NUM_FILES; i++)); do
    filename=$(printf "payload_%04d.payload.json" "$i")
    filepath="$OUTPUT_DIR/$filename"

    # Generate JSON payload
    payload=$(generate_json_payload "$SIZE" "$ENTROPY" "$i")

    # Create JSON content with pretty formatting
    if [[ ${#HEADER_NAMES[@]} -gt 0 ]]; then
        # Build headers JSON object
        headers_json="{"
        first=true
        for idx in "${!HEADER_NAMES[@]}"; do
            header_name="${HEADER_NAMES[$idx]}"
            values_spec="${HEADER_VALUE_SPECS[$idx]}"
            header_value=$(select_header_value "$values_spec")

            if [[ "$first" == "true" ]]; then
                first=false
            else
                headers_json+=","
            fi
            headers_json+="\"$header_name\":\"$header_value\""
        done
        headers_json+="}"

        jq -n \
            --argjson data "$payload" \
            --argjson headers "$headers_json" \
            '{value: $data, headers: $headers}' > "$filepath"
    else
        jq -n \
            --argjson data "$payload" \
            '{value: $data}' > "$filepath"
    fi

    # Calculate actual size
    actual_size=$(echo -n "$payload" | wc -c | tr -d ' ')
    SIZES+=("$actual_size")

    # Progress update
    if [[ $((i + 1)) -eq 1 ]] || [[ $(((i + 1) % 100)) -eq 0 ]] || [[ $((i + 1)) -eq $NUM_FILES ]]; then
        echo "Generated $((i + 1))/$NUM_FILES files... (last file data size: $actual_size bytes)"
    fi
done

# Calculate statistics
total=0
min_size=${SIZES[0]}
max_size=${SIZES[0]}

for size in "${SIZES[@]}"; do
    total=$((total + size))
    if [[ $size -lt $min_size ]]; then
        min_size=$size
    fi
    if [[ $size -gt $max_size ]]; then
        max_size=$size
    fi
done

avg_size=$(echo "scale=1; $total / $NUM_FILES" | bc)
size_variance=$((max_size - min_size))
variance_percent=$(echo "scale=1; ($size_variance / $avg_size) * 100" | bc)

echo
echo "Generation complete!"
echo "Average payload size: $avg_size bytes"
echo "Min payload size: $min_size bytes"
echo "Max payload size: $max_size bytes"
echo "Size variance: $size_variance bytes ($variance_percent%)"