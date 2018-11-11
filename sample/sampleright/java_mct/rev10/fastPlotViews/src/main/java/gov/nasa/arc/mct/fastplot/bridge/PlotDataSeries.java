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
import gov.nasa.arc.mct.fastplot.bridge.PlotConstants.PlotLineConnectionType;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;

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
	public static final Stroke EMPTY_STROKE = new Stroke() {
		private final Shape EMPTY_SHAPE = new Polygon();
		@Override
		public Shape createStrokedShape(Shape p) {
			return EMPTY_SHAPE;
		}
	};

	private final static Logger logger = LoggerFactory
			.getLogger(PlotDataSeries.class);

	private PlotterPlot plot;
	CompressingXYDataset dataset;
	Color color;
	LinearXYPlotLine linePlot;
	LegendEntry legendEntry;

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
		/* Note that once a LegendEntry acquires a plot line, it may 
		 * apply its own setup to it. */
		
		linePlot = new LinearXYPlotLineWrapper(plot.plotView.getXAxis(), plot.plotView.getYAxis(),
				plot.axisOrientation == AxisOrientationSetting.X_AXIS_AS_TIME ? XYDimension.X : XYDimension.Y);
		LineMode lineMode = LineMode.STEP_XY;
		if (plot.plotLineConnectionType == PlotLineConnectionType.DIRECT) {
			lineMode = LineMode.STRAIGHT;
		}
		linePlot.setLineMode(lineMode);
		linePlot.setForeground(color);
		
		if (plot.plotLineDraw.drawMarkers()) {
			for (int i = 0; i < PlotConstants.MAX_NUMBER_OF_DATA_ITEMS_ON_A_PLOT; i++) {
				if (PlotLineColorPalette.getColor(i).getRGB() == color.getRGB()) {
					//linePlot.setPointFill(PlotLineShapePalette.getShape(i));
					linePlot.setPointIcon(new PlotMarkerIcon(PlotLineShapePalette.getShape(i)));
				}
			}
		}
		
        if (!plot.plotLineDraw.drawLine()) {
            linePlot.setStroke(EMPTY_STROKE);
        }
		
		plot.plotView.getContents().add(linePlot);
		if (legendEntry != null) {
			legendEntry.setPlot(linePlot);
		}
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
}
