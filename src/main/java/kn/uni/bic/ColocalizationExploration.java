/*-
 * #%L
 * Interactive Command for colocalization exploration
 * %%
 * Copyright (C) 2017 University of Konstanz
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package kn.uni.bic;

import java.util.EnumSet;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import net.imagej.display.DatasetView;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.scijava.Cancelable;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import sc.fiji.coloc.algorithms.Histogram2D;
import sc.fiji.coloc.algorithms.Histogram2D.DrawingFlags;
import sc.fiji.coloc.algorithms.MissingPreconditionException;
import sc.fiji.coloc.gadgets.DataContainer;

/**
 * TODO Documentation
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BIC>Colocalization Influences")
public class ColocalizationExploration<T extends RealType<T>> implements
	Command, Cancelable, Interactive
{

	@Parameter(label = "Noise", min = "5", max = "50", style = NumberWidget.SPINNER_STYLE, callback = "parameterChanged", persist=false)
	private int noise = 30;

	@Parameter(label = "Relative intensity of Blue to Yellow", min = "0",
		max = "10", stepSize = "0.1", style = NumberWidget.SPINNER_STYLE, callback = "parameterChanged", persist=false)
	private double relativeIntensity = 1.0;

	@Parameter(label = "Translation distance [px]", min = "0",
			max = "10", style = NumberWidget.SPINNER_STYLE, callback = "parameterChanged", persist=false)
	private int translation = 0;

	@Parameter
	private UIService uiService;

	@Parameter
	private OpService opService;
	
	@Parameter
	private ScriptService scriptService;

	private Dataset blueDataset;
	private Dataset yellowDataset;
	private Dataset histogramDataset;

	@Parameter
	private DisplayService displayService;

	@Parameter
	private ImageDisplayService imageDisplayService;

	@Parameter
	private DatasetService datasetService;
	
	private Localizable blueCenter = new Point(200, 200);
	private Localizable yellowCenter;

	private int radius = 100;

	private ImageDisplay blueDisplay;

	private ImageDisplay yellowDisplay;

	private ImageDisplay histogramDisplay;
	
	UnaryComputerOp<UnsignedByteType, UnsignedByteType> noiseOp;
	
	private boolean canceled = false;
	
	@Override
	public void run() {
//		updateDisplay();
	}

	/**
	 * @return
	 */
	private Img<UnsignedByteType> createYellowImg() {
		yellowCenter = new Point(blueCenter.getLongPosition(0) + translation, blueCenter.getLongPosition(1) + translation);
		Img<UnsignedByteType> yellow = opService.create().img(new FinalInterval(new long[] {0, 0}, new long[] {400, 400}), new UnsignedByteType());
		Cursor<UnsignedByteType> yellowCursor = yellow.localizingCursor();
		while (yellowCursor.hasNext()) {
			yellowCursor.fwd();
			long x = yellowCursor.getLongPosition(0);
			long y = yellowCursor.getLongPosition(1);

			double distance = Math.floor(Math.sqrt(Math.pow(x - yellowCenter.getLongPosition(0), 2) + Math.pow(y - yellowCenter.getLongPosition(1), 2)));
			if (distance < radius) {
				yellowCursor.get().set((int) ((radius - distance)/(radius)*(relativeIntensity<1 ? 1 : 1/relativeIntensity)*250));
			}
		}

		// Add noise
		IterableInterval<UnsignedByteType> noisyYellow = opService.map((IterableInterval) yellow, noiseOp, new UnsignedByteType());
		Img<UnsignedByteType> noisyYellowCopy= PlanarImgs.unsignedBytes(Intervals.dimensionsAsLongArray(noisyYellow));
		opService.copy().iterableInterval(noisyYellowCopy, noisyYellow);
		return noisyYellowCopy;
	}

	/**
	 * @return
	 */
	private Img<UnsignedByteType> createBlueImg() {
		// Create blue image with spot
		Img<UnsignedByteType> blue = opService.create().img(new FinalInterval(new long[] {0, 0}, new long[] {400, 400}), new UnsignedByteType());
		Cursor<UnsignedByteType> blueCursor = blue.localizingCursor();
		while (blueCursor.hasNext()) {
			blueCursor.fwd();
			long x = blueCursor.getLongPosition(0);
			long y = blueCursor.getLongPosition(1);

			double distance = Math.floor(Math.sqrt(Math.pow(x - blueCenter.getLongPosition(0), 2) + Math.pow(y - blueCenter.getLongPosition(1), 2)));
			if (distance < radius) {
				blueCursor.get().set((int) ((radius - distance)/(radius)*(relativeIntensity<1?relativeIntensity:1)*250));
			}
		}

		// Add noise
		noiseOp = (UnaryComputerOp) opService.op(Ops.Filter.AddNoise.class, UnsignedByteType.class, UnsignedByteType.class, 0d, 255d, (double) noise);
		IterableInterval<UnsignedByteType> noisyBlue = opService.map((IterableInterval) blue, noiseOp, new UnsignedByteType());
		Img<UnsignedByteType> noisyBlueCopy = PlanarImgs.unsignedBytes(Intervals.dimensionsAsLongArray(noisyBlue));
		opService.copy().iterableInterval(noisyBlueCopy, noisyBlue);

		return noisyBlueCopy;
	}

	/**
	 * This main function serves for development purposes. It allows you to run
	 * the plugin immediately out of your integrated development environment
	 * (IDE).
	 *
	 * @param args whatever, it's ignored
	 * @throws Exception
	 */
	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run(ColocalizationExploration.class, true);
	}

	private Histogram2D<UnsignedByteType> executeColoc2() {
		Histogram2D<UnsignedByteType> histogram2d = new Histogram2D<UnsignedByteType>("Something", false, EnumSet.of( DrawingFlags.Plot ));
		DataContainer<UnsignedByteType> container = new DataContainer<UnsignedByteType>((RandomAccessibleInterval) blueDataset, (RandomAccessibleInterval) yellowDataset, 1, 1, "Blue", "Yellow");
		try {
			histogram2d.execute(container);
		}
		catch (MissingPreconditionException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}
		return histogram2d;
	}

	@SuppressWarnings("unused")
	private void parameterChanged() {
		Img<UnsignedByteType> noisyBlueCopy = createBlueImg();
		Dataset blueTemp = datasetService.create(noisyBlueCopy);
		if (blueDataset == null) {
			blueDataset = blueTemp;
			blueDisplay = (ImageDisplay) displayService.createDisplay("Blue", blueDataset);
		} else {
			blueDataset.copyDataFrom(blueTemp);
		}

		Img<UnsignedByteType> noisyYellowCopy = createYellowImg();
		Dataset yellowTemp = datasetService.create(noisyYellowCopy);
		if (yellowDataset == null) {
			yellowDataset = yellowTemp;
			yellowDisplay = (ImageDisplay) displayService.createDisplay("Yellow", yellowDataset);
		} else {
			yellowDataset.copyDataFrom(yellowTemp);
		}

		RandomAccessibleInterval<LongType> histogram = executeColoc2().getPlotImage();
		IterableInterval<BitType> apply = opService.threshold().apply(Views.iterable(histogram), new LongType(1));
		Img<BitType> img = opService.create().img(histogram, new BitType());
		opService.copy().iterableInterval((IterableInterval<BitType>) img, apply);
		Dataset histogramTemp = datasetService.create(img);
		if (histogramDataset == null) {
			histogramDataset = histogramTemp;
			histogramDisplay = (ImageDisplay) displayService.createDisplay("Histogram", histogramDataset);
		} else {
			histogramDataset.copyDataFrom(histogramTemp);
		}
	}

	@Override
	public boolean isCanceled() {
		return this.canceled;
	}

	@Override
	public void cancel(String reason) {
		blueDisplay.close();
		yellowDisplay.close();
		histogramDisplay.close();
	}

	@Override
	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}
	
	/** Updates the displayed min/max range to match min and max values. */
	private void updateDisplay() {
		DatasetView activeDatasetView = imageDisplayService.getActiveDatasetView(
			histogramDisplay);
		if (activeDatasetView != null) {
			activeDatasetView.setChannelRanges(0.0, 10.0);
			activeDatasetView.update();
		}
	}

}
