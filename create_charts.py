#!/usr/bin/env python

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

    for name, results in workload_results.items():
        print 'Generating charts for', name

        create_chart('Publish latency 99pct',
                     y_label='Latency (ms)',
                     time_series=[(x['driver'], x['publishLatency99pct']) for x in results])

        create_chart('Publish rate',
                     y_label='Rate (msg/s)',
                     time_series=[(x['driver'], x['publishRate']) for x in results])


def create_chart(title, y_label, time_series):
    line_chart = pygal.Line()
    line_chart.title = title

    line_chart.human_readable = True
    line_chart.y_title = y_label
    line_chart.x_title = 'Time (seconds)'
    line_chart.x_labels = [str(10 * x) for x in range(len(time_series[0][1]))]

    for label, values in time_series:
        line_chart.add(label, values)

    line_chart.range = (0, max(chain(* [l for (x, l) in time_series])) * 1.20)
    line_chart.render_to_file(title + '.svg')


if __name__ == '__main__':
    create_charts(sys.argv[1:])
