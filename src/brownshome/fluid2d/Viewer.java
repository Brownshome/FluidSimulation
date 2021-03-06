package brownshome.fluid2d;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class Viewer extends JPanel {
	public static void main(String[] args) {
		int gridSize, workGroups;
		
		if(args.length != 0) {
			gridSize = Integer.parseInt(args[0]);
			workGroups = Integer.parseInt(args[1]);
		} else {
			gridSize = 150;
			workGroups = 24;
		}
		
		SwingUtilities.invokeLater(() -> Viewer.startApplication(gridSize, workGroups));
	}
	
	private static void startApplication(int gridSize, int gran) {
		Viewer viewer = new Viewer(gridSize, gran);
		
		JFrame frame = new JFrame();
		frame.getContentPane().add(viewer);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
	    frame.setLocationRelativeTo(null);
	    frame.setResizable(false);
		frame.setVisible(true);
		frame.addKeyListener(viewer.listener);
	}
	
	private final FluidSimulation simulation;
	private final KeyListener listener;
	
	public Viewer(int gridSize, int gran) {
		super(true);
		
		simulation = new FluidSimulation(gridSize, gridSize, 0.001, 0.0, gran);
		
		new Thread("Simulation Thread") {
			public void run() {
				simulation.startTimer();
				while(true) {
					simulation.tick();
				}
			};
		}.start();
		
		new Timer(100, e -> {
			repaint();
		}).start();
		
		listener = new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
				
			}
			
			@Override public void keyTyped(KeyEvent e) {
				simulation.switchColourMode();
			}
			
			@Override public void keyPressed(KeyEvent e) {}
		};
	}
	
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(1000, 1000);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		simulation.paint(g, getSize());
	}
}
