#!/usr/bin/env python
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


import sys
import json
import pygal
from itertools import chain


def create_charts(test_results):

    # Group results for same workload
    workload_results = {}
    for test_result in test_results:
        result = json.load(open(test_result))
        if not result['workload'] in workload_results:
            workload_results[result['workload']] = []
        workload_results[result['workload']].append(result)

    for workload, results in workload_results.items():
        print('Generating charts for', workload)
        workload = workload.replace('/', '-')

        create_chart(workload, 'Publish latency 99pct',
                     y_label='Latency (ms)',
                     time_series=[(x['driver'], x['publishLatency99pct']) for x in results])

        create_chart(workload, 'Publish Delay latency 99pct',
                     y_label='Latency (us)',
                     time_series=[(x['driver'], x['publishDelayLatency99pct']) for x in results])

        create_chart(workload, 'Publish rate',
                     y_label='Rate (msg/s)',
                     time_series=[(x['driver'], x['publishRate']) for x in results])

        create_chart(workload, 'Publish Error rate',
                     y_label='Rate (err/s)',
                     time_series=[(x['driver'], x['publishErrorRate']) for x in results])

        create_chart(workload, 'End To End Latency 95pct',
                     y_label='Latency (ms)',
                     time_series=[(x['driver'], x['endToEndLatency95pct']) for x in results])

        create_chart(workload, 'Consume rate',
                     y_label='Rate (msg/s)',
                     time_series=[(x['driver'], x['consumeRate']) for x in results])

        create_chart(workload, 'End To End Latency Avg',
                     y_label='Latency Avg (msec)',
                     time_series=[(x['driver'], x['endToEndLatencyAvg']) for x in results])

        create_quantile_chart(workload, 'Publish Latency Quantiles',
                              y_label='Latency (ms)',
                              time_series=[(x['driver'], x['aggregatedPublishLatencyQuantiles']) for x in results])

        create_quantile_chart(workload, 'Publish Delay Latency Quantiles',
                              y_label='Latency (us)',
                              time_series=[(x['driver'], x['aggregatedPublishDelayLatencyQuantiles']) for x in results])

        create_quantile_chart(workload, 'End To End Latency Quantiles',
                              y_label='Latency (ms)',
                              time_series=[(x['driver'], x['aggregatedEndToEndLatencyQuantiles']) for x in results])


def create_chart(workload, title, y_label, time_series):
    chart = pygal.XY(dots_size=.3,
                     legend_at_bottom=True,)
    chart.title = title

    chart.human_readable = True
    chart.y_title = y_label
    chart.x_title = 'Time (seconds)'
    # line_chart.x_labels = [str(10 * x) for x in range(len(time_series[0][1]))]

    for label, values in time_series:
        chart.add(label, [(10*x, y) for x, y in enumerate(values)])

    chart.range = (0, max(chain(* [l for (x, l) in time_series])) * 1.20)
    chart.render_to_file('%s - %s.svg' % (workload, title))


def create_quantile_chart(workload, title, y_label, time_series):
    import math
    chart = pygal.XY(  # style=pygal.style.LightColorizedStyle,
                     # fill=True,
                     legend_at_bottom=True,
                     x_value_formatter=lambda x: '{} %'.format(100.0 - (100.0 / (10**x))),
                     show_dots=True,
                     dots_size=.3,
                     show_x_guides=True)
    chart.title = title
    # chart.stroke = False

    chart.human_readable = True
    chart.y_title = y_label
    chart.x_title = 'Percentile'
    chart.x_labels = [1, 2, 3, 4, 5]

    for label, values in time_series:
        values = sorted((float(x), y) for x, y in values.items())
        xy_values = [(math.log10(100 / (100 - x)), y) for x, y in values if x <= 99.999]
        chart.add(label, xy_values)

    chart.render_to_file('%s - %s.svg' % (workload, title))


if __name__ == '__main__':
    create_charts(sys.argv[1:])
