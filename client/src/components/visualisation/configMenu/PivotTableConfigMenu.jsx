import React, { Component, PropTypes } from 'react';
import SelectInput from './SelectInput';
import Subtitle from './Subtitle';
import UniqueValueMenu from './UniqueValueMenu';

// For now, we only support a subset of the regular aggregation options
const aggregationOptions = [
  {
    value: 'mean',
    label: 'mean',
  },
  {
    value: 'max',
    label: 'max',
  },
  {
    value: 'min',
    label: 'min',
  },
  {
    value: 'count',
    label: 'count',
  },
  {
    value: 'sum',
    label: 'sum',
  },
];

export default class PivotTableConfigMenu extends Component {
  constructor() {
    super();
    this.state = {
      catValMenuCollapsed: true,
      rowValMenuCollapsed: true,
    };
  }

  render() {
    const {
      visualisation,
      onChangeSpec,
      columnOptions,
    } = this.props;
    const spec = visualisation.spec;

    return (
      <div>
        <hr />
        <Subtitle>Aggregation</Subtitle>
        <div>
          <span
            className="aggregationInputContainer"
          >
            <SelectInput
              placeholder="Select aggregation method"
              labelText="Aggregation method"
              choice={spec.aggregation !== null ? spec.aggregation.toString() : null}
              name="aggregationMethod"
              options={aggregationOptions}
              disabled={spec.rowColumn == null || spec.categoryColumn == null}
              onChange={(value) => {
                const change = { aggregation: value };

                if (value === 'count') {
                  change.valueColumn = null;
                }
                onChangeSpec(change);
              }}
            />
            <div
              className="helpText aggregationHelpText"
            >
              <div className="helpTextContainer">
                <span className="alert">!</span>
                Choose a category column and a row column to use aggregations other than count.
              </div>
            </div>
          </span>
          {spec.aggregation !== 'count' &&
            <div>
              <SelectInput
                placeholder="Select a value column"
                labelText="Value column"
                choice={spec.valueColumn !== null ? spec.valueColumn.toString() : null}
                name="valueColumnInput"
                options={columnOptions.filter(option =>
                  option.type === 'number' || option.type === 'date')}
                onChange={value => onChangeSpec({
                  valueColumn: value,
                })}
                clearable
              />
              <div className="inputGroup">
                <label htmlFor="decimalPlacesInput">
                  Number of decimal places
                </label>
                <input
                  className="numberInput"
                  id="decimalPlacesInput"
                  type="number"
                  value={spec.decimalPlaces}
                  min={0}
                  max={16}
                  onChange={evt => onChangeSpec({
                    decimalPlaces: evt.target.value,
                  })}
                />
              </div>
            </div>
          }
        </div>
        <hr />
        <Subtitle>Categories</Subtitle>
        <SelectInput
          placeholder="Select a category column"
          labelText="Category column"
          choice={spec.categoryColumn !== null ? spec.categoryColumn.toString() : null}
          name="categoryColumnInput"
          options={columnOptions}
          onChange={(value) => {
            const change = { categoryColumn: value };

            if (value == null && spec.aggregation !== 'count') {
              change.aggregation = 'count';
              change.valueColumn = null;
            }
            onChangeSpec(change);
          }}
          clearable
        />
        {spec.categoryColumn !== null &&
          <UniqueValueMenu
            tableData={visualisation.data}
            dimension="category"
            collapsed={this.state.catValMenuCollapsed}
            onChangeSpec={this.props.onChangeSpec}
            column={spec.categoryColumn}
            filters={spec.filters}
            toggleCollapsed={() =>
              this.setState({ catValMenuCollapsed: !this.state.catValMenuCollapsed })
            }
          />
        }
        <hr />
        <Subtitle>Rows</Subtitle>
        <SelectInput
          placeholder="Select a row column"
          labelText="Row column"
          choice={spec.rowColumn !== null ? spec.rowColumn.toString() : null}
          name="rowColumnInput"
          options={columnOptions}
          onChange={(value) => {
            const change = { rowColumn: value };

            if (value == null && spec.aggregation !== 'count') {
              change.aggregation = 'count';
              change.valueColumn = null;
            }
            onChangeSpec(change);
          }}
          clearable
        />
        {spec.rowColumn !== null &&
          <UniqueValueMenu
            tableData={visualisation.data}
            dimension="row"
            collapsed={this.state.rowValMenuCollapsed}
            onChangeSpec={this.props.onChangeSpec}
            column={spec.rowColumn}
            filters={spec.filters}
            toggleCollapsed={() =>
              this.setState({ rowValMenuCollapsed: !this.state.rowValMenuCollapsed })
            }
          />
        }
      </div>
    );
  }
}

PivotTableConfigMenu.propTypes = {
  visualisation: PropTypes.object.isRequired,
  datasets: PropTypes.object.isRequired,
  onChangeSpec: PropTypes.func.isRequired,
  columnOptions: PropTypes.array.isRequired,
  aggregationOptions: PropTypes.array.isRequired,
};
