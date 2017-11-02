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

        create_quantile_chart('Publish latency quantiles',
                              y_label='Latency (ms)',
                              time_series=[(x['driver'], x['aggregatedPublishLatencyQuantiles']) for x in results])


def create_chart(title, y_label, time_series):
    chart = pygal.XY()
    chart.title = title

    chart.human_readable = True
    chart.y_title = y_label
    chart.x_title = 'Time (seconds)'
    # line_chart.x_labels = [str(10 * x) for x in range(len(time_series[0][1]))]

    for label, values in time_series:
        chart.add(label, [(10*x, y) for x, y in enumerate(values)])

    chart.range = (0, max(chain(* [l for (x, l) in time_series])) * 1.20)
    chart.render_to_file(title + '.svg')


def create_quantile_chart(title, y_label, time_series):
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

    chart.render_to_file('%s.svg' % title)


if __name__ == '__main__':
    create_charts(sys.argv[1:])
