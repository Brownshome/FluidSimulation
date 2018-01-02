package brownshome.fluid2d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

import brownshome.vecmath.IVec2;
import brownshome.vecmath.MVec2;
import brownshome.vecmath.Vec2;

public class FluidSimulation {
	final int gridWidth;
	final int gridHeight;
	double timestep;
	final double viscocity;
	private boolean fixedTimestep = false;
	private long sims = 0;
	private long start = 0;
	private final int granularity;
	
	private FluidCellArray array;

	public FluidSimulation(int gridWidth, int gridHeight, double viscocity, double timestep, int granularity) {
		array = new FluidCellArray(gridWidth, gridHeight);

		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.viscocity = viscocity;
		this.granularity = granularity;
		
		if(timestep != 0.0) {
			fixedTimestep = true;
			this.timestep = timestep;
		}
	}

	public double simSpeed() {
		return 1e9 *  sims / (System.nanoTime() - start);
	}
	
	public void startTimer() {
		start = System.nanoTime();
		sims = 0;
	}
	
	public void tick() {
		double maxVelocity = 0.0;
		
		if(!fixedTimestep) {
			for(int y = 0; y < gridHeight; y++) {
				for(int x = 0; x < gridWidth; x++) {
					maxVelocity = Math.max(maxVelocity, array.velocitySq(x, y));
				}
			}

			maxVelocity = Math.sqrt(maxVelocity);
			
			if(maxVelocity == 0)
				timestep = 0.01;
			else
				timestep = 2.0 / maxVelocity;
			
			System.out.printf("%f\n", timestep);
		}
		
		foreach((x, y) -> array.advection(x, y, timestep));
		
		synchronized(this) {
			foreach((x, y) -> {
				array.pushVelocityChange(x, y);
				array.pushColourChange(x, y);
			});
		}
		
		for(int i = 0; i < 80; i++) { 
			foreach((x, y) -> array.performDiffusionIteration(x, y, viscocity, timestep));
			foreach(array::pushVelocityChange);
		}

		for(int i = 0; i < 50; i++) {
			foreach(array::calculatePressure);
			foreach(array::pushPressureChange);
		}

		foreach((x, y) -> {
			array.subtractPressureGradient(x, y);
			array.pushVelocityChange(x, y);
			array.applyForce(x, y);
			array.pushVelocityChange(x, y);
		});
		
		sims++;
	}

	@FunctionalInterface
	private interface GridCall {
		void call(int x, int y);
	}
	
	private void foreachSingleThreaded(GridCall func) {
		for(int x = 0; x < gridWidth; x++) {
			for(int y = 0; y < gridHeight; y++) {
				func.call(x, y);
			}
		}
	}

	private final ExecutorService threadPool = Executors.newFixedThreadPool(8);
	private void foreach(GridCall func) {
		int blockHeight = (gridHeight - 1) / granularity + 1;
		
		CountDownLatch latch = new CountDownLatch(granularity);
		
		for(int block = 0; block < granularity; block++) {
			final int blockStorage = block;
			
			threadPool.submit(() -> {
				int limit = Math.min(gridHeight, blockHeight * (blockStorage + 1));
				
				for(int y = blockHeight * blockStorage; y < limit; y++) {
					for(int x = 0; x < gridWidth; x++) {
						func.call(x, y);
					}
				}
				
				latch.countDown();
			});
		}
		
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	public void paint(Graphics g, Dimension dimension) {
		double width, height;
		width = dimension.getWidth() / gridWidth;
		height = dimension.getHeight() / gridHeight;

		synchronized(this) {
			foreachSingleThreaded((x, y) -> {
				g.setColor(array.getColour(x, y));
				g.fillRect((int) (x * width), (int) (y * height), 1 + (int) width, 1 + (int) height);
			});
		}
		
		g.setFont(new Font("Dialog", Font.BOLD, 20));
		g.setColor(Color.WHITE);
		g.drawString(String.format("SPS: %.1f", simSpeed()), 20, 30);
	}

	public void switchColourMode() {
		array.colourMode = (array.colourMode + 1) % 3;
	}
}
