package brownshome.fluid2d;

import java.awt.Color;

import brownshome.vecmath.MVec2;
import brownshome.vecmath.Vec2;

public class FluidCellArray {
	static final int DATA_SIZE = 11;
	
	static final int VELOCITY_OFFSET = 0; //2
	static final int COLOUR_OFFSET = 2; //3
	static final int PRESSURE_OFFSET = 5; //1
	static final int TMP_VELOCITY_OFFSET = 6; //2
	static final int TMP_COLOUR_OFFSET = 8; //3
	
	private final double[] data;
	private final int width;
	private final int height;

	public int colourMode;
	
	FluidCellArray(int width, int height) {
		this.width = width;
		this.height = height;
		
		data = new double[width * height * DATA_SIZE];
		
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				double r = (x * 10 / width) % 2 == 0 ? 1.0 : 0.0;
				double g = (y * 10 / height) % 2 == 0 ? 1.0 : 0.0;
				double b = 1.0;

				colour(index(x, y), r, g, b);
				velocity(index(x, y), -10.0, 0.0);
			}
		}
	}

	private int index(int x, int y) {
		return (x + y * width) * DATA_SIZE;
	}

	private int get(int x, int y) {
		if(x < 0) {
			x = 0;
		} else if(x >= width) {
			x = width - 1;
		}
		
		if(y < 0) {
			y = 0;
		} else if(y >= height) {
			y = height - 1;
		}
		
		return index(x, y);
	}

	private int get(double x, double y) {
		return get((int) x, (int) y);
	}

	/** Finds a property at the grid point (x, y) */
	double sample(Vec2 vec, int offset) {
		//What do we do at the edge of the area? For now we will wrap around to simplify boundary conditions.
		MVec2 tmp = new MVec2(vec);

		double p00 = data[offset + get(vec.x(), vec.y())];
		double p01 = data[offset + get(vec.x(), vec.y() + 1)];
		double p10 = data[offset + get(vec.x() + 1, vec.y())];
		double p11 = data[offset + get(vec.x() + 1, vec.y() + 1)];

		while(tmp.x() < 0) 
			tmp.add(width, 0.0);

		while(tmp.y() < 0) 
			tmp.add(0.0, height);

		tmp.set(tmp.x() % 1.0, tmp.y() % 1.0);
		vec = tmp;
		
		return lerp(lerp(p00, p10, vec.x()), lerp(p01, p11, vec.x() % 1.0), vec.y());
	}

	private double lerp(double a, double b, double lerp) {
		return (1 - lerp) * a + lerp * b;
	}
	
	void advection(int x, int y, double timestep) {
		int index = index(x, y);
		
		//Sample the four squares around the target square
		double sampleX = x - velocityX(index) * timestep;
		double sampleY = y - velocityY(index) * timestep;
		
		int i00 = get(sampleX, sampleY);
		int i01 = get(sampleX, sampleY + 1.0);
		int i10 = get(sampleX + 1.0, sampleY);
		int i11 = get(sampleX + 1.0, sampleY + 1.0);

		sampleX += width;
		sampleY += height;
		
		sampleX %= 1.0;
		sampleY %= 1.0;
		
		assert sampleX >= 0 && sampleY >= 0;
		
		tmpVelocity(index, 
				lerp(lerp(velocityX(i00), velocityX(i10), sampleX), lerp(velocityX(i01), velocityX(i11), sampleX), sampleY),
				lerp(lerp(velocityY(i00), velocityY(i10), sampleX), lerp(velocityY(i01), velocityY(i11), sampleX), sampleY));
				
		tmpColour(index, 
				lerp(lerp(colourR(i00), colourR(i10), sampleX), lerp(colourR(i01), colourR(i11), sampleX), sampleY),
				lerp(lerp(colourG(i00), colourG(i10), sampleX), lerp(colourG(i01), colourG(i11), sampleX), sampleY),
				lerp(lerp(colourB(i00), colourB(i10), sampleX), lerp(colourB(i01), colourB(i11), sampleX), sampleY));
	}

	private void tmpVelocity(int index, double x, double y) {
		data[index + TMP_VELOCITY_OFFSET] = x;
		data[index + TMP_VELOCITY_OFFSET + 1] = y;
	}

	private void tmpColour(int index, double r, double g, double b) {
		data[index + TMP_COLOUR_OFFSET] = r;
		data[index + TMP_COLOUR_OFFSET + 1] = b;
		data[index + TMP_COLOUR_OFFSET + 2] = g;
	}

	private double velocityX(int index) {
		return data[index + VELOCITY_OFFSET];
	}

	private double velocityY(int index) {
		return data[index + VELOCITY_OFFSET + 1];
	}

	private void velocity(int index, double vx, double vy) {
		data[index + VELOCITY_OFFSET] = vx;
		data[index + VELOCITY_OFFSET + 1] = vy;
	}

	private double tmpVelocityX(int index) {
		return data[index + TMP_VELOCITY_OFFSET];
	}

	private double tmpVelocityY(int index) {
		return data[index + TMP_VELOCITY_OFFSET + 1];
	}

	private double colourR(int index) {
		return data[index + COLOUR_OFFSET];
	}

	private double colourG(int index) {
		return data[index + COLOUR_OFFSET + 1];
	}

	private double colourB(int index) {
		return data[index + COLOUR_OFFSET + 2];
	}

	private void colour(int index, double r, double g, double b) {
		data[index + COLOUR_OFFSET] = r;
		data[index + COLOUR_OFFSET + 1] = b;
		data[index + COLOUR_OFFSET + 2] = g;
	}

	private double tmpColourR(int index) {
		return data[index + TMP_COLOUR_OFFSET];
	}

	private double tmpColourG(int index) {
		return data[index + TMP_COLOUR_OFFSET + 1];
	}

	private double tmpColourB(int index) {
		return data[index + TMP_COLOUR_OFFSET + 2];
	}

	private double tmpPressure(int index) {
		return data[index + TMP_COLOUR_OFFSET];
	}

	private void tmpPressure(int index, double newPressure) {
		data[index + TMP_COLOUR_OFFSET] = newPressure;
	}

	private double pressure(int right) {
		return data[right + PRESSURE_OFFSET];
	}

	private void pressure(int index, double pressure) {
		data[index + PRESSURE_OFFSET] = pressure;
	}

	void performDiffusionIteration(int x, int y, double viscocity, double timestep) {
		int index = index(x, y);
		int top, left, right, bottom;

		top = get(x, y + 1);
		left = get(x - 1, y);
		bottom = get(x, y - 1);
		right = get(x + 1, y);

		double alpha = 1.0 / viscocity / timestep;
		double beta = 4 + alpha;

		double vx = velocityX(top) + velocityX(left) + velocityX(bottom) + velocityX(right) + velocityX(index) * alpha;
		double vy = velocityY(top) + velocityY(left) + velocityY(bottom) + velocityY(right) + velocityY(index) * alpha;
		
		vx /= beta;
		vy /= beta;
		
		tmpVelocity(index, vx, vy);
	}

	double divergence(int x, int y) {
		int top, left, right, bottom;

		top = get(x, y + 1);
		left = get(x - 1, y);
		bottom = get(x, y - 1);
		right = get(x + 1, y);

		return ((velocityX(right) - velocityX(left)) + (velocityY(top) - velocityY(bottom))) * 0.5;
	}

	void subtractPressureGradient(int x, int y) {
		int index = index(x, y);
		int top, left, right, bottom;

		top = get(x, y + 1);
		left = get(x - 1, y);
		bottom = get(x, y - 1);
		right = get(x + 1, y);

		double pressureDx = pressure(right) - pressure(left);
		double pressureDy = pressure(top) - pressure(bottom);

		tmpVelocity(index, velocityX(index) - pressureDx, velocityY(index) - pressureDy);
	}

	void pushVelocityChange(int x, int y) {
		int index = index(x, y);
		
		if(isSolid(x, y)) {
			velocity(index, 0.0, 0.0);
		} else {
			velocity(index, tmpVelocityX(index), tmpVelocityY(index));
		}
	}

	void pushPressureChange(int x, int y) {
		int index = index(x, y);
		pressure(index, tmpPressure(index));
	}

	void pushColourChange(int x, int y) {
		int index = index(x, y);
		colour(index, tmpColourR(index), tmpColourG(index), tmpColourB(index));
	}

	void applyForce(int x, int y) {
		int index = index(x, y);

		if(y < height * 13/21 && y > height * 8/21 && x < width * 4/7 && x > width * 3/7) {    
			tmpVelocity(index, velocityX(index) - 100.0, velocityY(index));
		}
	}

	void calculatePressure(int x, int y) {
		int index = index(x, y);
		int top, left, right, bottom;

		top = get(x, y + 1);
		left = get(x - 1, y);
		bottom = get(x, y - 1);
		right = get(x + 1, y);

		double newPressure = pressure(top) + pressure(left) + pressure(bottom) + pressure(right) - 1 * 1 * divergence(x, y);
		newPressure *= 0.25;
		
		tmpPressure(index, newPressure);
	}

	public Color getColour(int x, int y) {
		int index = index(x, y);
		
		if(isSolid(x, y))
			return Color.BLACK;
		
		switch(colourMode) {
		case 0:
			double r = colourR(index);
			double g = colourG(index);
			double b = colourB(index);
			
			double acc = r * r + g * g + b * b;
			acc = Math.sqrt(acc);

			r = r / acc;
			g = g / acc;
			b = b / acc;

			return new Color(clamp((float) r), clamp((float) g), clamp((float) b));
		case 1:
			return new Color(exp(velocityX(index) * 0.0005), exp(velocityY(index) * 0.0005), 1f);
		case 2:
			double pressure = pressure(index);
			return new Color(exp(pressure * 0.005), exp(pressure * 0.00005), exp(pressure * 0.0000005));
		}
		
		return null;
	}
	

	float clamp(float f) {
		return Math.min(Math.max(0, f), 1);
	}

	boolean isSolid(int x, int y) {
		if(x == 0 || y == 0 || x == width - 1 || y == height - 1) {
			return true;
		}
		
		return x < width * 4/7 && x > width * 3/7 
				&& y < height * 4/7 && y > height * 3/7
				&& !(y < height * 23/42 && x < width * 23/42);
	}
	
	private float exp(double x) {
		return (float) (Math.atan(x) / Math.PI + 0.5);
	}

	double velocitySq(int x, int y) {
		int index = index(x, y);
		double vx, vy;
		vx = velocityX(index);
		vy = velocityY(index);
		
		return vx * vx + vy * vy;
	}
}