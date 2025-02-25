import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;

import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import edu.wpi.first.wpilibj.networktables.NetworkTable;

public class TowerTracker {

	/**
	 * static method to load opencv and networkTables
	 */
	///////////////////////////////////////////////////////////////////
	//*****************************************************************
	//THIS IS IMPORTANT INFORMATION
	//CAMERA SETTINGS:
	//	Color level: 100
	//	Brightness:27
	//	Sharpness: 50
	//	Contrast:72
	//	WhiteBalance: Fixed Fluorescent 1
	//	Exposure value: 27
	//	Exposure control: automatic
	//	Back light compensation: checked
	//	Exposure zones: auto
	//	Exposure priority: default
	//
	static{ 
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		NetworkTable.setClientMode();
		NetworkTable.setIPAddress("roborio-1741-frc.local");
	}
//	Constants for RGB values
	public static final Scalar 
		RED = new Scalar(0, 0, 255),
		BLUE = new Scalar(255, 0, 0),
		GREEN = new Scalar(0, 255, 0),
		BLACK = new Scalar(0,0,0),
		YELLOW = new Scalar(0, 255, 255),
//		Lower and upper bounds of the HSV filtering
		LOWER_BOUNDS = new Scalar(71,36,100),
		UPPER_BOUNDS = new Scalar(154,255,255);//lower:(73,53,220) Upper:(94,255,255)

//	Random variables
	public static VideoCapture videoCapture;
	public static Mat matInput, matOriginal, matHSV, matThresh, clusters, matHeirarchy;
	public static NetworkTable table;

	//////////////////  DO NOT EDIT  //////////////////////
//	Constants for known variables
	public static final int TOP_TARGET_HEIGHT = 99;
	public static final int TOP_CAMERA_HEIGHT = 12;

//	Camera detail constants
	public static final double VERTICAL_FOV  = 34;
	public static final double HORIZONTAL_FOV  = 49;
	public static final double VERTICAL_CAMERA_ANGLE = 55;
	public static final double HORIZONTAL_CAMERA_ANGLE = 0;
	///////////////////////////////////////////////////////

//	Main loop variable
	public static boolean shouldRun = true;
	
//	Display variables
	public static JFrame frame;
	public static JLabel lbl;
	public static ImageIcon image;

	public static void main(String[] args) {
//		Initialize the matrixes
		matOriginal = new Mat();
		matHSV = new Mat();
		matThresh = new Mat();
		clusters = new Mat();
		matHeirarchy = new Mat();
		
//		Set the network table to use
		table = NetworkTable.getTable("Targeting");
		
//		Initialize the GUI
	    frame=new JFrame();
	    frame.setLayout(new FlowLayout());
	    frame.setSize(1350, 1000);
	    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    frame.setVisible(true);
		
//		Main loop
		while(shouldRun){
			try {
//				Opens up the camera stream and tries to load it
				System.out.println("Initializing camera...");
				videoCapture = new VideoCapture();
				
				System.out.println("Opening stream...");
				videoCapture.open("http://axis-1741.local/mjpg/video.mjpg");
				
				System.out.println("Checking connection...");
//				Wait until it is opened
				while(!videoCapture.isOpened()){}
				
				System.out.println("Opened successfully...");
				
//				Actually process the image
				processImage();
				System.out.println("Finished processing...");
				
			} catch (Exception e) {
//				Catch any errors and print them to the console
				System.out.println("Uh oh...");
				e.printStackTrace();
				//break;
			}
		}
//		Exit the Java process when the loop finishes
		System.out.println("Releasing video stream...");
		videoCapture.release();
		System.out.println("Exiting application...");
		System.exit(0);
	}

	public static void processImage(){
		System.out.println("Processing...");
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		double x,y,targetAngle,distance,width,pan,tilt;
		Rect bestRec = new Rect(0,0,0,0);
		String output = new String();
	    
	    long lastTime = 0;
	    long temp = 0;
	    double fps = 0;

		while(true){
//			Clear variables from previous loop
			contours.clear();
			output = "";
			
//			Calculate the fps
			temp = lastTime;
			if(((lastTime = System.currentTimeMillis()) - temp) != 0)
			{
				fps = 1000/((lastTime = System.currentTimeMillis()) - temp); //This way, lastTime is assigned and used at the same time.
			}
			System.out.println(fps);
			
//			Capture image from the axis camera
			videoCapture.read(matOriginal);
			matInput = matOriginal.clone();
			
//			Convert the image type from BGR to HSV
			Imgproc.cvtColor(matOriginal, matHSV, Imgproc.COLOR_BGR2HSV);
			
//			Filter out any colors not inside the threshold
			Core.inRange(matHSV, LOWER_BOUNDS, UPPER_BOUNDS, matThresh);
			
//			Find the contours
			Imgproc.findContours(matThresh, contours, matHeirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

//			Make sure the contours that are detected are at least 30x30 pixels and a valid aspect ratio
			for (Iterator<MatOfPoint> iterator = contours.iterator(); iterator.hasNext();) {
				MatOfPoint matOfPoint = (MatOfPoint) iterator.next();
				Rect rec = Imgproc.boundingRect(matOfPoint);
				if(rec.height < 30 || rec.width < 30){
					iterator.remove();
					drawContour(matOriginal, rec, BLUE);
					continue;
				}
				
				float aspect = (float)rec.width/(float)rec.height;
				if(aspect > 2.5 || aspect < 0.75){
					drawContour(matOriginal, rec, YELLOW);
					iterator.remove();
				}
			}
			
//			Draw the final contour rectangles on the material
			for(MatOfPoint mop : contours){
				Rect rec = Imgproc.boundingRect(mop);
				drawContour(matOriginal, rec, GREEN);
			}
			
			bestRec = new Rect(0,0,0,0);
//			Calculate targeting output for each of the remaining contours
			for (int p = 0; p < contours.size(); p++) {
				Rect rec = Imgproc.boundingRect(contours.get(p));
				
				if(rec.width > bestRec.width){
					bestRec = rec;
				}
			}

//			If there was actually a target
			if(bestRec.width != 0){
//				Horizontal angle to target
				x = bestRec.br().x - (bestRec.width / 2);
//				Set x to +/- 1 using the position on the screen
				x = ((2 * (x / matOriginal.width())) - 1);
				pan = HORIZONTAL_CAMERA_ANGLE-(x*HORIZONTAL_FOV /2.0);

//				Vertical angle to target
				y = bestRec.br().y + (bestRec.height / 2);
//				Set y to +/- 1 using the position on the screen
				y = ((2 * (y / matOriginal.height())) - 1);
				tilt = VERTICAL_CAMERA_ANGLE-(y*VERTICAL_FOV /2.0);
				
//				Calculate the horizontal distance to the goal
				targetAngle = (y * VERTICAL_FOV / 2) + VERTICAL_CAMERA_ANGLE;
				distance = (TOP_TARGET_HEIGHT - TOP_CAMERA_HEIGHT)/
						Math.tan(Math.toRadians(targetAngle));
				width = bestRec.width;
				
//				Draw values on target
				Point center = new Point(bestRec.br().x,bestRec.br().y+10);
				Point center1 = new Point(bestRec.br().x,bestRec.br().y+25);
				Point center2 = new Point(bestRec.br().x,bestRec.br().y+40);
				Imgproc.putText(matOriginal, String.format("%.2f",pan), center, Core.FONT_HERSHEY_PLAIN, 1, RED);
				Imgproc.putText(matOriginal, String.format("%.2f",tilt), center1, Core.FONT_HERSHEY_PLAIN, 1, RED);
				Imgproc.putText(matOriginal, String.format("%.2f",width), center2, Core.FONT_HERSHEY_PLAIN, 1, RED);

				output = String.format("%.2f,%.2f,%.2f", width, pan, tilt);
//				System.out.println(output);
			}
			
//			Build the display debugging window
			frame.getContentPane().removeAll();
			image = new ImageIcon(createAwtImage(matInput));
			JLabel label1 = new JLabel(image);
			label1.setAlignmentX(JLabel.LEFT_ALIGNMENT);
			label1.setAlignmentY(JLabel.TOP_ALIGNMENT);
			frame.getContentPane().add(label1);
			
			image = new ImageIcon(createAwtImage(matHSV));
			JLabel label2 = new JLabel(image);
			label2.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
			label2.setAlignmentY(JLabel.TOP_ALIGNMENT);
			frame.getContentPane().add(label2);
			
			image = new ImageIcon(createAwtImage(matThresh));
			JLabel label3 = new JLabel(image);
			label3.setAlignmentX(JLabel.LEFT_ALIGNMENT);
			label3.setAlignmentY(JLabel.BOTTOM_ALIGNMENT);
			frame.getContentPane().add(label3);
			
			image = new ImageIcon(createAwtImage(matOriginal));
			JLabel label4 = new JLabel(image);
			label4.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
			label4.setAlignmentY(JLabel.BOTTOM_ALIGNMENT);
			frame.getContentPane().add(label4);
			
//			Force the display frame to update
			SwingUtilities.updateComponentTreeUI(frame);
			
//			Write the output sting to the network table
			table.putString("targets", output);
//			System.out.println(output);
		}
	}
	
//	Draws the supplied rectangle on the supplied material using the supplied color
	public static void drawContour(Mat drawMat, Rect tempRect, Scalar color){
		Imgproc.rectangle(drawMat, tempRect.br(), tempRect.tl(), color);
	}
	
//	Takes in a matrix and outputs an image usable by the GUI
	public static BufferedImage createAwtImage(Mat mat) {
	    int type = 0;
	    if (mat.channels() == 1) {
	        type = BufferedImage.TYPE_BYTE_GRAY;
	    } else if (mat.channels() == 3) {
	        type = BufferedImage.TYPE_3BYTE_BGR;
	    } else {
	        return null;
	    }

	    BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
	    WritableRaster raster = image.getRaster();
	    DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();
	    byte[] data = dataBuffer.getData();
	    mat.get(0, 0, data);

	    return image;
	}
}
