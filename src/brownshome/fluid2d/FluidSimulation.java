package brownshome.fluid2d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

import brownshome.vecmath.MVec2;
import brownshome.vecmath.Vec2;

public class FluidSimulation {
	private class FluidCell {
		final int x, y;

		final MVec2 velocity;
		final MVec2 newVelocity = new MVec2();
		double pressure, newPressure;
		final double[] colour, newColour = new double[3];

		FluidCell(double pressure, MVec2 velocity, int x, int y, double[] colour) {
			this.velocity = velocity;
			this.pressure = pressure;
			this.x = x;
			this.y = y;
			this.colour = colour;
		}

		void advection() {
			MVec2 tmp = new MVec2(x, y);
			tmp.scaleAdd(velocity, -timestep);

			newVelocity.set(sample(tmp, cell -> cell.velocity.x()), sample(tmp, cell -> cell.velocity.y()));

			for(int i = 0; i < 3; i++) {
				final int k = i;
				newColour[k] = sample(tmp, cell -> cell.colour[k]);
			}
		}

		void performDiffusionIteration() {
			FluidCell top, left, right, bottom;

			top = get(x, y + 1);
			left = get(x - 1, y);
			bottom = get(x, y - 1);
			right = get(x + 1, y);

			double alpha = 1.0 / viscocity / timestep;
			double beta = 4 + alpha;

			newVelocity.set(top.velocity);
			newVelocity.add(left.velocity);
			newVelocity.add(bottom.velocity);
			newVelocity.add(right.velocity);
			newVelocity.scaleAdd(velocity, alpha);
			newVelocity.scale(1.0 / beta);
		}

		double divergence() {
			FluidCell top, left, right, bottom;

			top = get(x, y + 1);
			left = get(x - 1, y);
			bottom = get(x, y - 1);
			right = get(x + 1, y);

			return ((right.velocity.x() - left.velocity.x()) + (top.velocity.y() - bottom.velocity.y())) * 0.5;
		}

		/** Removes divergence by subtracting the pressure gradient, does not need updating */
		void subtractPressureGradient() {
			FluidCell top, left, right, bottom;

			top = get(x, y + 1);
			left = get(x - 1, y);
			bottom = get(x, y - 1);
			right = get(x + 1, y);

			MVec2 pressureGradient = new MVec2(right.pressure - left.pressure, top.pressure - bottom.pressure);
			pressureGradient.scale(0.5);

			newVelocity.set(velocity);
			newVelocity.subtract(pressureGradient);
			pushVelocityChange();
		}

		void pushVelocityChange() {
			if(isSolid(x, y)) {
				velocity.set(0.0, 0.0);
			} else {
				velocity.set(newVelocity);
			}
		}

		void pushPressureChange() {
			pressure = newPressure;
		}

		void pushColourChange() {
			colour[0] = newColour[0];
			colour[1] = newColour[1];
			colour[2] = newColour[2];
		}

		void applyForce() {
			newVelocity.set(velocity);

			if(y < gridHeight * 13/21 && y > gridHeight * 8/21 && x < gridWidth * 4/7 && x > gridWidth * 3/7) {
				newVelocity.add(-100, 0);
			}

			pushVelocityChange();
		}

		void calculatePressure() {
			FluidCell top, left, right, bottom;

			top = get(x, y + 1);
			left = get(x - 1, y);
			bottom = get(x, y - 1);
			right = get(x + 1, y);

			newPressure = top.pressure + left.pressure + bottom.pressure + right.pressure - 1 * 1 * divergence();
			newPressure *= 0.25;
		}

		public Color getColour() {
			if(isSolid(x, y))
				return Color.BLACK;
			
			switch(colourMode) {
			case 0:
				double acc = colour[0] * colour[0] + colour[1] * colour[1] + colour[2] * colour[2];
				acc = Math.sqrt(acc);

				double r, g, b;
				r = newColour[0] / acc;
				g = newColour[1] / acc;
				b = newColour[2] / acc;

				return new Color(clamp((float) r), clamp((float) g), clamp((float) b));
			case 1:
				MVec2 v = new MVec2(velocity);
				v.scale(0.01);

				return new Color(exp(v.x() * 0.05), exp(v.y() * 0.05), 1f);
			case 2:
				return new Color(exp(pressure * 0.005), exp(pressure * 0.00005), exp(pressure * 0.0000005));
			}
			
			return null;
		}

		private float exp(double x) {
			return (float) (Math.atan(x) / Math.PI + 0.5);
		}
	}

	private final int gridWidth, gridHeight;
	private final FluidCell[][] area;
	private double timestep;
	private final double viscocity;
	private int colourMode;
	private boolean fixedTimestep = false;

	public FluidSimulation(int gridWidth, int gridHeight, double viscocity, double timestep) {
		area = new FluidCell[gridHeight][gridWidth];

		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.viscocity = viscocity;
		
		if(timestep != 0.0) {
			fixedTimestep = true;
			this.timestep = timestep;
		}

		for(int y = 0; y < gridHeight; y++) {
			for(int x = 0; x < gridWidth; x++) {
				MVec2 tmp = new MVec2(0.0, 0.0);

				double[] colour = new double[] { (x * 10 / gridWidth) % 2 == 0 ? 1.0 : 0.0, (y * 10 / gridHeight) % 2 == 0 ? 1.0 : 0.0, 1.0 };

				area[y][x] = new FluidCell(0.0, tmp, x, y, colour);
			}
		}
	}

	private boolean isSolid(int x, int y) {
		if(x == 0 || y == 0 || x == gridWidth - 1 || y == gridHeight - 1) {
			return true;
		}
		
		return x < gridWidth * 4/7 && x > gridWidth * 3/7 
				&& y < gridHeight * 4/7 && y > gridHeight * 3/7
				&& (y > gridHeight * 11/21 || y < gridHeight * 10/21);
	}

	public void tick() {
		double maxVelocity = 0.0;
		
		if(!fixedTimestep) {
			for(int y = 0; y < gridHeight; y++) {
				for(int x = 0; x < gridWidth; x++) {
					maxVelocity = Math.max(maxVelocity, area[y][x].velocity.lengthSq());
				}
			}

			maxVelocity = Math.sqrt(maxVelocity);
			timestep = 0.5 / maxVelocity;
			if(maxVelocity == 0)
				timestep = 0.01;
			
			System.out.printf("%f\n", timestep);
		}
		
		foreach(FluidCell::advection);
		foreach(FluidCell::pushVelocityChange);
		foreach(FluidCell::pushColourChange);

		for(int i = 0; i < 80; i++) { 
			foreach(FluidCell::performDiffusionIteration);
			foreach(FluidCell::pushVelocityChange);
		}

		for(int i = 0; i < 50; i++) {
			foreach(FluidCell::calculatePressure);
			foreach(FluidCell::pushPressureChange);
		}

		foreach(FluidCell::subtractPressureGradient);
		foreach(FluidCell::applyForce);
	}

	private void foreach(Consumer<FluidCell> func) {
		for(int row = 0; row <  gridHeight; row++) {
			for(int col = 0; col < gridWidth; col++) {
				func.accept(get(row, col));
			}
		}
	}

	private FluidCell get(int x, int y) {
		if(x < 0) {
			x = 0;
		} else if(x >= gridWidth) {
			x = gridWidth - 1;
		}
		
		if(y < 0) {
			y = 0;
		} else if(y >= gridHeight) {
			y = gridHeight - 1;
		}
		
		return area[y][x];
	}

	private FluidCell get(double x, double y) {
		return get((int) x, (int) y);
	}

	/** Finds a property at the grid point (x, y) */
	private double sample(Vec2 vec, ToDoubleFunction<FluidCell> func) {
		//What do we do at the edge of the area? For now we will wrap around to simplify boundary conditions.
		MVec2 tmp = new MVec2(vec);

		double p00 = func.applyAsDouble(get(vec.x(), vec.y()));
		double p01 = func.applyAsDouble(get(vec.x(), vec.y() + 1));
		double p10 = func.applyAsDouble(get(vec.x() + 1, vec.y()));
		double p11 = func.applyAsDouble(get(vec.x() + 1, vec.y() + 1));

		while(tmp.x() < 0) 
			tmp.add(gridWidth, 0.0);

		while(tmp.y() < 0) 
			tmp.add(0.0, gridHeight);

		vec = tmp;
		
		return lerp(lerp(p00, p10, vec.x() % 1.0), lerp(p01, p11, vec.x() % 1.0), vec.y() % 1.0);
	}

	private double lerp(double a, double b, double lerp) {
		return (1 - lerp) * a + lerp * b;
	}

	public void paint(Graphics g, Dimension dimension) {
		double width, height;
		width = dimension.getWidth() / gridWidth;
		height = dimension.getHeight() / gridHeight;

		foreach(cell -> {
			g.setColor(cell.getColour());

			g.fillRect((int) (cell.x * width), (int) (cell.y * height), 1 + (int) width, 1 + (int) height);
		});
	}

	private float clamp(float f) {
		return Math.min(Math.max(0, f), 1);
	}

	public void switchColourMode() {
		colourMode = (colourMode + 1) % 3;
	}
}
