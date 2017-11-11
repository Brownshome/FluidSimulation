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
		SwingUtilities.invokeLater(Viewer::startApplication);
	}
	
	private static void startApplication() {
		Viewer viewer = new Viewer();
		
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
	
	public Viewer() {
		super(true);
		
		simulation = new FluidSimulation(200, 200, 0.001, 0.1);
		
		new Timer(16, e -> {
			simulation.tick();
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
