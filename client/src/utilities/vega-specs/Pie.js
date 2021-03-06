export default function getVegaPieSpec(visualisation, data, containerHeight, containerWidth) {
  const chartRadius = containerHeight < containerWidth ? containerHeight / 3 : containerWidth / 3;
  const innerRadius = visualisation.visualisationType === 'donut' ?
    Math.floor(chartRadius / 1.75) : 0;
  const dataArray = data.map(item => item);

  const layoutTransform = {
    name: 'pie',
    source: 'table',
    transform: [
      {
        type: 'pie',
        field: 'bucketCount',
      },
      {
        type: 'formula',
        field: 'rounded_value',
        expr: 'floor(datum.bucketCount * 1000) / 1000', // round label to 3 decimal places
      },
      {
        type: 'formula',
        field: 'percentage',
        expr: '((datum.layout_end - datum.layout_start) / (2 * PI)) * 100',
      },
      {
        type: 'formula',
        field: 'rounded_percentage',
        // round percentage to 2 decimal places for labels
        expr: 'floor(datum.percentage * 100) / 100',
      },

    ],
  };

  dataArray.push(layoutTransform);

  const dataSource = 'pie';
  const segmentLabelField = 'bucketValue';
  const fieldY = 'bucketCount';
  const showLegend = visualisation.spec.showLegend;

  return ({
    data: dataArray,
    width: showLegend ? (chartRadius * 2) : containerWidth - 20,
    height: containerHeight - 45,
    padding: {
      top: 35,
      right: 10 + showLegend ? containerWidth - (chartRadius * 2) - 10 : 0,
      bottom: 10,
      left: 10,
    },
    scales: [
      {
        name: 'r',
        type: 'ordinal',
        domain: {
          data: dataSource,
          field: fieldY,
        },
        range: [chartRadius],
      },
      {
        name: 'c',
        type: 'ordinal',
        range: 'category10',
        domain: {
          data: dataSource,
          field: segmentLabelField,
        },
      },
    ],
    marks: [
      {
        type: 'arc',
        from: {
          data: dataSource,
        },
        properties: {
          enter: {
            x: {
              field: {
                group: 'width',
              },
              mult: 0.5,
            },
            y: {
              field: {
                group: 'height',
              },
              mult: 0.5,
            },
            startAngle: {
              field: 'layout_start',
            },
            endAngle: {
              field: 'layout_end',
            },
            innerRadius: {
              value: innerRadius,
            },
            outerRadius: {
              value: chartRadius,
            },
            stroke: {
              value: 'white',
            },
          },
          update: {
            fill: [
              {
                test: 'datum._id == tooltip._id || datum._id == textTooltip._id',
                value: 'pink',
              },
              {
                scale: 'c',
                field: segmentLabelField,
              },
            ],
          },
        },
      },
      showLegend ?
        {
          type: 'text',
        }
        :
        {
          type: 'text',
          from: {
            data: dataSource,
          },
          properties: {
            enter: {
              x: {
                field: {
                  group: 'width',
                },
                mult: 0.5,
              },
              y: {
                field: {
                  group: 'height',
                },
                mult: 0.5,
              },
              radius: {
                scale: 'r',
                field: 'value',
                offset: 30,
              },
              theta: {
                field: 'layout_mid',
              },
              fill: {
                value: 'black',
              },
              align: {
                value: 'center',
              },
              text: {
                field: segmentLabelField,
              },
            },
          },
        },
      {
        type: 'text',
        from: {
          data: dataSource,
          transform: [
            {
              type: 'filter',
              test: 'datum._id == tooltip._id || datum._id == textTooltip._id',
            },
          ],
        },
        properties: {
          enter:
            showLegend ?
              {
                x: {
                  value: (chartRadius * 2) + 20,
                },
                y: {
                  value: chartRadius + 35,
                },
                fill: {
                  value: 'black',
                },
                align: {
                  value: 'left',
                },
                text: {
                  template: `{{datum[${segmentLabelField}]}}: {{datum.rounded_value}} ({{datum.rounded_percentage}}%)`,
                },
              }
              :
              {
                x: {
                  field: {
                    group: 'width',
                  },
                  mult: 0.5,
                },
                y: {
                  field: {
                    group: 'height',
                  },
                  mult: 0.5,
                },
                radius: {
                  scale: 'r',
                  field: 'a',
                  offset: -1 * (chartRadius / 5),
                },
                theta: {
                  field: 'layout_mid',
                },
                fill: {
                  value: 'black',
                },
                align: {
                  value: 'center',
                },
                text: {
                  template: '{{datum.rounded_value}} ({{datum.rounded_percentage}}%)',
                },
              }
            ,
        },
      },
    ],
    legends:
      showLegend ?
        [
          {
            fill: 'c',
            orient: 'right',
            title: 'Legend',
            properties: {
              symbols: {
                shape: {
                  value: 'square',
                },
              },
            },
          },
        ]
        :
        [],
    signals: [
      {
        name: 'tooltip',
        init: {},
        streams: [
          {
            type: 'arc:mouseover',
            expr: 'datum',
          },
          {
            type: 'arc:mouseout',
            expr: '{}',
          },
        ],
      },
      {
        name: 'textTooltip',
        init: {},
        streams: [
          {
            type: 'text:mouseover',
            expr: 'datum',
          },
          {
            type: 'text:mouseout',
            expr: '{}',
          },
        ],
      },
    ],
  });
}
