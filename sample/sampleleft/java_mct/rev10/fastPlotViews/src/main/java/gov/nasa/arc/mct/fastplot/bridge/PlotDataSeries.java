/*******************************************************************************
 * Mission Control Technologies, Copyright (c) 2009-2012, United States Government
 * as represented by the Administrator of the National Aeronautics and Space 
 * Administration. All rights reserved.
 *
 * The MCT platform is licensed under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 *
 * MCT includes source code licensed under additional open source licenses. See 
 * the MCT Open Source Licenses file included with this distribution or the About 
 * MCT Licenses dialog available at runtime from the MCT Help menu for additional 
 * information. 
 *******************************************************************************/
package gov.nasa.arc.mct.fastplot.bridge;

import gov.nasa.arc.mct.fastplot.bridge.PlotConstants.AxisOrientationSetting;

import java.awt.BasicStroke;
import java.awt.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import plotter.DoubleData;
import plotter.xy.CompressingXYDataset;
import plotter.xy.CompressingXYDataset.MinMaxChangeListener;
import plotter.xy.DefaultCompressor;
import plotter.xy.LinearXYPlotLine;
import plotter.xy.LinearXYPlotLine.LineMode;
import plotter.xy.XYDimension;

/**
 * Holds the information for a Quinn-Curtis data series representing a single
 * entry on a plot. This includes, the data, the line plot representing it, the
 * plotting color, the legend, and the code for compressing data as it is put
 * into the buffers.
 */
class PlotDataSeries implements MinMaxChangeListener {

	private final static Logger logger = LoggerFactory
			.getLogger(PlotDataSeries.class);

	private PlotterPlot plot;
	CompressingXYDataset dataset;
	Color color;
	LinearXYPlotLine linePlot;
	LegendEntry legendEntry;
	LinearXYPlotLine regressionLine;

	private String dataSetName;

	PlotDataSeries(PlotterPlot thePlot, String dataSetName, Color theColor) {
		plot = thePlot;
		color = theColor;
		this.dataSetName = dataSetName;
		setupLinePlot();
		setupDataSet(dataSetName);
	};


	/**
	 * Resets the series' buffered data.
	 */
	void resetData() {
		logger.debug("PlotDataSeries.resetData()");
		// remove the process variable for this item from the plot
		plot.plotView.getContents().remove(linePlot);
		if (regressionLine != null) {
			plot.plotView.getContents().remove(regressionLine);
		}
		setupLinePlot();
		setupDataSet(dataSetName);
	}

	private void setupDataSet(String dataSetName) {

		assert plot.plotView != null : "Plot Object not initalized";
		assert linePlot != null;

		dataset = new CompressingXYDataset(linePlot, new DefaultCompressor());
		// Listen for min/max changes on the non-time axis
		if(plot.axisOrientation == AxisOrientationSetting.X_AXIS_AS_TIME) {
			dataset.addYMinMaxChangeListener(this);
		} else {
			dataset.addXMinMaxChangeListener(this);
		}

		// add limit manager
		// Add alarms which under pin notifications for when data goes out of
		// range
		// and the limit triangles are put on the plot.
		plot.limitManager.addLimitAlarms(dataset);
		
	}

	private void setupLinePlot() {
		linePlot = new LinearXYPlotLine(plot.plotView.getXAxis(), plot.plotView.getYAxis(),
				plot.axisOrientation == AxisOrientationSetting.X_AXIS_AS_TIME ? XYDimension.X : XYDimension.Y);
		linePlot.setLineMode(LineMode.STEP_YX);
		linePlot.setForeground(color);

		plot.plotView.getContents().add(linePlot);
		if (legendEntry != null) {
			legendEntry.setPlot(linePlot);
		}
		if (legendEntry != null && legendEntry.hasRegressionLine()) {
			addRegressionLine();
		}
	}
	
	/**
	 * 
	 */
	private void addRegressionLine() {
		regressionLine = new LinearXYPlotLine(plot.plotView.getXAxis(), plot.plotView.getYAxis(),
				plot.axisOrientation == AxisOrientationSetting.X_AXIS_AS_TIME ? XYDimension.X : XYDimension.Y);
		regressionLine.setLineMode(LineMode.STRAIGHT);
		regressionLine.setForeground(color);
		regressionLine.setStroke(new BasicStroke(PlotConstants.SLOPE_LINE_WIDTH,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10.0f, PlotConstants.dash1, 0.0f));
		plot.plotView.getContents().add(regressionLine);
		legendEntry.setHasRegressionLine(true);
		legendEntry.setRegressionLine(regressionLine);
	}
	
	private void removeRegressionLine() {
		plot.plotView.getContents().remove(regressionLine);
		plot.plotView.getContents().validate();
		plot.plotView.getContents().repaint();
		regressionLine = null;
	}

	/** Get the regression line for this data series
	 * @return regressionLine
	 */
	public LinearXYPlotLine getRegressionLine() {
		return regressionLine;
	}
	
	/** Set the regression line object for this data series
	 * @param line a LinearXYPlotLine
	 */
	public void setRegressionLine(LinearXYPlotLine line) {
		regressionLine = line;
	}

	public String getDataSetName() {
		return dataSetName;
	}

	void setPlottingColor(Color theColor) {
		color = theColor;
	}

	void setLegend(LegendEntry legend) {
		legendEntry = legend;
		legendEntry.setPlotLabelingAlgorithm(plot.plotLabelingAlgorithm);
	}

	void setLinePlot(LinearXYPlotLine thePlot) {
		linePlot = thePlot;
	}

	LinearXYPlotLine getPlot() {
		return linePlot;
	}

	LegendEntry getLegendEntry() {
		return legendEntry;
	}

	Color getColor() {
		return color;
	}

	CompressingXYDataset getData() {
		return dataset;
	}

	/**
	 * Compress data in local plot buffer by fifty percent.
	 */
	public void compressByFiftyPercent() {
		// FIXME: Handle this in the dataset
	}

	/**
	 * Search through the data set to identify the maximum value in the range
	 * 
	 * @param maxTime
	 *            upper bound on time range to search
	 * @param minTime
	 *            lower bound on the time range to search
	 * @return minimum value in data set between max and max time (inclusive)
	 */
	double[] getMaxValue(long maxTime, long minTime) {
		double max = -Double.MAX_VALUE;
		double time = 0;
		DoubleData nonTimeData = getNonTimeData(dataset);
		DoubleData timeData = getTimeData(dataset);

		if (timeData != null) {
			int n = timeData.getLength();
			for (int i = 0; i < n; i++) {
				double nonTime = nonTimeData.get(i);

					// System.out.println("min time " + minTime + " Max Time " +
					// maxTime + " timeData " + timeData[i] + " = " +
					// nonTimeData[i]);
					double time2 = timeData.get(i);
					if (time2 >= minTime && time2 <= maxTime) {
						if (nonTime > max) {
							max = nonTime;
							time = time2;
						}
					}
			}
		}
		double[] result = new double[2];
		result[0] = max;
		result[1] = time;
		return result;
	}

	/**
	 * Search through the data set to identify the minimum value in the range
	 * 
	 * @param maxTime
	 *            upper bound on time range to search
	 * @param minTime
	 *            lower bound on the time range to search
	 * @return minimum value in data set between min and max time (inclusive)
	 */
	double[] getMinValue(long maxTime, long minTime) {
		double min = Double.MAX_VALUE;
		double time = 0;
		DoubleData nonTimeData = getNonTimeData(dataset);
		DoubleData timeData = getTimeData(dataset);
		if (timeData != null) {
			int n = timeData.getLength();
			for (int i = 0; i < n; i++) {
				double time2 = timeData.get(i);
					if (time2 >= minTime && time2 <= maxTime) {
						double nonTime = nonTimeData.get(i);
						if (nonTime < min) {
							min = nonTime;
							time = time2;
						}
					}
			}
		}
		double[] result = new double[2];
		result[0] = min;
		result[1] = time;
		return result;
	}

	DoubleData getNonTimeData(CompressingXYDataset dataSet) {
		if(plot.axisOrientation == AxisOrientationSetting.X_AXIS_AS_TIME) {
			return dataSet.getYData();
		} else {
			return dataSet.getXData();
		}
	}

	DoubleData getTimeData(CompressingXYDataset dataSet) {
		if(plot.axisOrientation == AxisOrientationSetting.X_AXIS_AS_TIME) {
			return dataSet.getXData();
		} else {
			return dataSet.getYData();
		}
	}


	@Override
	public void minMaxChanged(CompressingXYDataset dataset, XYDimension dimension) {
		plot.minMaxChanged();
	}


	@Override
	public String toString() {
		int size = dataset.getPointCount();
		if(size == 0) {
			return "Full Data: Number data points 0\n";
		} else {
			return "Full Data: current value " + dataset.getYData().get(size - 1) + " Number data points " + size + "\n";
		}
	}
	
	public void updateRegressionLine() {
		if (getLegendEntry() != null) {
			if (getLegendEntry().hasRegressionLine()) {
				if (regressionLine == null) {
					addRegressionLine();
				}
			} else {
				if (regressionLine != null) {
					removeRegressionLine();
				}
				return;
			}
			regressionLine.removeAllPoints();
			// Not enough data for regression
			if (getData().getPointCount() < legendEntry.getNumberRegressionPoints()) {
				return;
			}
			double[] xRegData = new double[legendEntry.getNumberRegressionPoints()];
			double[] yRegData = new double[legendEntry.getNumberRegressionPoints()];
			
			int xDataLength = getData().getXData().getLength();
			int yDataLength = getData().getYData().getLength();
			int shift = 0;
			//Get last n x and y values that are valid values
			for (int i = legendEntry.getNumberRegressionPoints(); i > 0; i--) {
				while (
						Double.isNaN(getData().getXData().get(xDataLength - (legendEntry.getNumberRegressionPoints() - i) - 1 - shift)) ||
								Double.isNaN(getData().getYData().get(yDataLength - (legendEntry.getNumberRegressionPoints() - i) - 1 - shift))) {
					if ((yDataLength - (legendEntry.getNumberRegressionPoints() - i) - 1 - (shift + 1)) < 0 || 
							(xDataLength - (legendEntry.getNumberRegressionPoints() - i) - 1 - (shift + 1)) < 0) {
						// Not enough data for regression
						return;
					} else {
						shift++;
					}
				}
 
				xRegData[i-1] = getData().getXData().get(xDataLength - (legendEntry.getNumberRegressionPoints() - i) - 1 - shift);
				yRegData[i-1] = getData().getYData().get(yDataLength - (legendEntry.getNumberRegressionPoints() - i) - 1 - shift);
			}
			double x = xRegData[legendEntry.getNumberRegressionPoints() - 1],  y=yRegData[legendEntry.getNumberRegressionPoints() - 1];
			HighPrecisionLinearRegression lr = new HighPrecisionLinearRegression(xRegData, yRegData);
			
			if (lr.getSlopeAsBigDecimal() != null &&
					lr.getIntercept() != null &&
				!Double.isNaN(lr.getSlope()) && 
				!Double.isInfinite(lr.getSlope()) &&
				!Double.isInfinite(lr.getIntercept().doubleValue()) &&
				!Double.isNaN(lr.getIntercept().doubleValue())		
				) {
				// Per Jira 3358 specs and customer discussions, translate regression line so that
				// origin is at last data point
				regressionLine.add(x, y); // Add origin of line
				if(linePlot.getIndependentDimension() == XYDimension.X) {				
					regressionLine.add(Long.valueOf(plot.getCurrentTimeAxisMaxAsLong()).doubleValue(), 
							lr.calculateY(Long.valueOf(plot.getCurrentTimeAxisMaxAsLong()).doubleValue()) +
							(y - lr.calculateY(x))); // Add terminus of line
				} 				else {
					regressionLine.add(lr.calculateX(Long.valueOf(plot.getCurrentTimeAxisMaxAsLong()).doubleValue()) +
							(x - lr.calculateX(y)),Long.valueOf(plot.getCurrentTimeAxisMaxAsLong()).doubleValue()); // Add terminus of line
				}
				regressionLine.repaint();

			}
		}
	}
}
