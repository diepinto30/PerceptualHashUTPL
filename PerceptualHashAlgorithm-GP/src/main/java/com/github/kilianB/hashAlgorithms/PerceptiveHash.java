package com.github.kilianB.hashAlgorithms;

import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.util.Objects;
import java.util.logging.Logger;

import org.jtransforms.dct.DoubleDCT_2D;
import org.jtransforms.utils.CommonUtils;

import com.github.kilianB.graphics.FastPixel;
import com.github.kilianB.graphics.ImageUtil;

/**
 * Calculate a hash based on the frequency of an image using the DCT T2. This
 * algorithm provides a very good accuracy and is robust to several image
 * transformations, usually providing much better distinction capability than
 * color or gradient based approaches.
 * <p>
 * Due to the nature of dct's the hashes generated by this algorithms bits do
 * not have a 50% chance of being set resulting in the normalized hamming
 * distance usually not covering the entire [0-1] range.
 * 
 * <p>
 * <b>Implnote:</b> In future versions this issue can possibly be addressed by
 * applying principal component analysis on a huge set of hashes and figuring
 * out which bits are holding the most information. Maybe we can fix this by
 * taking a look at the difference to a fixed cosine.
 * <p>
 * A lot of implementations around also compute the hash based on the mean value
 * and not the mean. Take a look at this as well.
 * 
 * @author Kilian
 * @since 1.0.0
 */
public class PerceptiveHash extends HashingAlgorithm {

	private static final long serialVersionUID = 8409228150836051697L;

	private static final Logger LOGGER = Logger.getLogger(PerceptiveHash.class.getSimpleName());

	/**
	 * The height and width of the scaled instance used to compute the hash
	 */
	private int height, width;

	/**
	 * 
	 * @param bitResolution The bit resolution specifies the final length of the
	 *                      generated hash. A higher resolution will increase
	 *                      computation time and space requirement while being able
	 *                      to track finer detail in the image. Be aware that a high
	 *                      key is not always desired.
	 */
	public PerceptiveHash(int bitResolution) {
		super(bitResolution);
		computeDimensions(bitResolution);

		// thread usage enabled issue warning.
		// This does not get triggered in usual circumstances.
		if (width * height >= CommonUtils.getThreadsBeginN_2D()) {
			LOGGER.warning(
					"Due to an unfortunate design decision in JTransform a threadpool will be kept alive after finishing calulation"
							+ " possibly block jvm termination.  You see this message because calculating an unusual high bit resolution perceptive hash will likly trigger this rule."
							+ " To quickly terminate the jvm without delay please call ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination(); manually once you are done computing perceptive hashes");
		}

	}

	@Override
	protected BigInteger hash(BufferedImage image, HashBuilder hash) {
		FastPixel fp = FastPixel.create(ImageUtil.getScaledInstance(image, width, height));

		int[][] lum = fp.getLuma();

		// int to double conversion ...
		double[][] lumAsDouble = new double[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				lumAsDouble[x][y] = lum[x][y] / 255d;
			}
		}

		DoubleDCT_2D dct = new DoubleDCT_2D(width, height);

		dct.forward(lumAsDouble, false);

		// Average value of the (topmost) YxY low frequencies. Skip the first column as
		// it might be too dominant. Solid color e.g.
		// TODO DCT walk down in a triangular motion. Skipping the entire edge neglects
		// several important frequencies. Maybe just skip
		// just the upper corner.
		double avg = 0;

		// Take a look at a forth of the pixel matrix. The lower right corner does not
		// yield much information.
		int subWidth = (int) (width / 4d);
		int subHeight = (int) (height / 4d);
		int count = subWidth * subHeight;

		// calculate the average of the dct
		for (int i = 1; i < subWidth + 1; i++) {
			for (int j = 1; j < subHeight + 1; j++) {
				avg += lumAsDouble[i][j] / count;
			}
		}

		for (int i = 1; i < subWidth + 1; i++) {
			for (int j = 1; j < subHeight + 1; j++) {

				if (lumAsDouble[i][j] < avg) {
					hash.prependZero();
				} else {
					hash.prependOne();
				}
			}
		}
		return hash.toBigInteger();
	}

	/**
	 * Compute the dimension for the resize operation. We want to get to close to a
	 * quadratic images as possible to counteract scaling bias.
	 * 
	 * @param bitResolution the desired resolution
	 */
	private void computeDimensions(int bitResolution) {

		// bitRes = (width/4)^2;
		int dimension = (int) Math.round(Math.sqrt(bitResolution)) * 4;
		// width //height
		int normalBound = ((dimension / 4) * (dimension / 4));
		int higherBound = ((dimension / 4) * (dimension / 4 + 1));

		this.width = dimension;
		this.height = dimension;

		if (higherBound < bitResolution) {
			this.width++;
			this.height++;
		} else {
			if (normalBound < bitResolution || (normalBound - bitResolution) > (higherBound - bitResolution)) {
				this.height += 4;
			}
		}
	}

	@Override
	protected int precomputeAlgoId() {
		return Objects.hash(getClass().getName(), height, width) * 31 + 1;
	}
}