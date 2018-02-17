package com.astraoracle.test;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import android.support.v7.app.ActionBarActivity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends ActionBarActivity
{
	private SeekBar      seekBar;
	private SeekBar      seekBar2;
	private TextView     textView;
	private TextView     statustext;
	private TextView     dronedata;
	private ToggleButton ctlBtn;
    private ToggleButton cutoffBtn;
	private EditText     editText1;
	private EditText     editText4;
	private EditText     editText5;
	
	private int     Throttle;
	private float   Roll;
	private float   Pitch;
	private int     Yaw=127;		// 0-255, middle - no yaw

    private float Kp, Ki, Kd;
    byte[]        BKp,BKd,BKi;

	private short rRoll;
	private short rPitch;
	private short rHDG;
	private short rALT;
    private byte  rYaw;
    private byte  rCtrl;
    private byte  rLoopTime;
    private short rThrottle;
    private short WifiAtt;
    private short BatLvl;

	private char[] anime={'-','\\','|','/'};
	private short animei=0;
	private short animeo=0;

	private static int BUFSIZE=25;
	private static int fq=50;
    byte[] sendbuf;
    byte[] receivebuf;
    DatagramSocket sockets;
    final byte[] bcastadr={(byte) 192,(byte) 168,(byte) 43,(byte) 255};

	private boolean dataready;
	private boolean threadsalive;
	private boolean threadralive;
	
	public void newparams()
    {
		 String s="  PHONE:\nBank=";
		 s+=Float.toString(Roll).substring(0, 3);		 
		 s+="\nPitch=";
		 s+=Float.toString(Pitch).substring(0, 3);				 
		 s+="\nYaw=";
		 s+=Integer.toString(Yaw);				 
		 s+="\nTHR=";
		 s+=Integer.toString(Throttle*100/255);
		 s+="%\n ";
		 textView.setText(s);	
		 sendbuf[0] ='Q';		// QCSVB - Let it be control_unit->drone protocol ID
		 sendbuf[1] ='C';
		 sendbuf[2] ='S';
		 sendbuf[3] ='V';
		 sendbuf[4] ='B';
		 sendbuf[5] =(byte)Throttle;
		 sendbuf[6] =(byte)Math.round(Roll+90);		 
		 sendbuf[7] =(byte)Math.round(Pitch+90);
		 sendbuf[8] =(byte)Yaw;
		 sendbuf[9] =(byte)((ctlBtn.isChecked())?1:0);		// 0 - NO YAW/ROLL CONTROL, AUTO LEVEL FLIGHT
		 sendbuf[10]=BKp[3];
		 sendbuf[11]=BKp[2];
		 sendbuf[12]=BKp[1];
		 sendbuf[13]=BKp[0];
		 sendbuf[14]=BKd[3];
		 sendbuf[15]=BKd[2];
		 sendbuf[16]=BKd[1];
		 sendbuf[17]=BKd[0];
		 sendbuf[18]=BKi[3];
		 sendbuf[19]=BKi[2];
		 sendbuf[20]=BKi[1];
		 sendbuf[21]=BKi[0];
         sendbuf[22]=(byte)((cutoffBtn.isChecked())?1:0);
		 sendbuf[23]=0;
		 dataready=true;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		threadsalive=false;
		threadralive=false;
		seekBar    = (SeekBar)  findViewById(R.id.seekBar1);
		seekBar2   = (SeekBar)  findViewById(R.id.seekBar2);
		textView   = (TextView) findViewById(R.id.TextView2);
		dronedata  = (TextView) findViewById(R.id.TextView1);
		statustext = (TextView) findViewById(R.id.textView3);
		ctlBtn     = (ToggleButton) findViewById(R.id.toggleButton1);
        cutoffBtn  = (ToggleButton) findViewById(R.id.toggleButton2);
		editText1  = (EditText) findViewById(R.id.editText1);
		editText4  = (EditText) findViewById(R.id.editText4);
		editText5  = (EditText) findViewById(R.id.editText5);
		sendbuf    = new byte[BUFSIZE];
		receivebuf = new byte[BUFSIZE];
		BKp = new byte[Float.SIZE];
		BKi = new byte[Float.SIZE];
		BKd = new byte[Float.SIZE];
		Log.i("QCSV", "Activity creation");

		try
        {
			sockets = new DatagramSocket();
			sockets.setBroadcast(true);			
		}
        catch (SocketException e)
        {
			statustext.setText(e.getMessage());			
			e.printStackTrace();
			Log.e("QCSV",e.getMessage());
		}
		
		SensorManager sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        final float[] mValuesMagnet      = new float[3];
        final float[] mValuesAccel       = new float[3];
        final float[] mValuesOrientation = new float[3];
        final float[] mRotationMatrix    = new float[9];
        
        final SensorEventListener mEventListener = new SensorEventListener()
        {
            public void onAccuracyChanged(Sensor sensor, int accuracy)
            {
            	// Do something if sensor accuracy became low...
            }

            public void onSensorChanged(SensorEvent event)
            {
                switch (event.sensor.getType())
                {
                    case Sensor.TYPE_ACCELEROMETER:
                        System.arraycopy(event.values, 0, mValuesAccel, 0, 3);
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        System.arraycopy(event.values, 0, mValuesMagnet, 0, 3);
                        break;
                }
//                if ((mValuesAccel!= null) && (mValuesMagnet!=null))
//                {
                	boolean success = SensorManager.getRotationMatrix(mRotationMatrix, null, mValuesAccel, mValuesMagnet);
                	if (success)
                    {
                		SensorManager.getOrientation(mRotationMatrix, mValuesOrientation);
                		Roll =(float) (mValuesOrientation[1]*180.0/3.14159);
                		Pitch=(float) (mValuesOrientation[2]*180.0/3.14159);            
                		newparams();
                	}
//                }
            }
        };

        sensorManager.registerListener(mEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(mEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), 
                SensorManager.SENSOR_DELAY_NORMAL);

		OnSeekBarChangeListener l;
		l=new OnSeekBarChangeListener()
        {
			 @Override
			 public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser)
             {
				 Throttle=progresValue;
				 newparams();			  
			 }
			@Override
			public void onStartTrackingTouch(SeekBar arg0)
            {
			}
			@Override
			public void onStopTrackingTouch(SeekBar arg0)
            {
			}
		};
		seekBar.setOnSeekBarChangeListener(l);

		OnSeekBarChangeListener l2;
		l2=new OnSeekBarChangeListener()
        {
				 @Override
				 public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser)
                 {
					 Yaw=progresValue;
					 newparams();			  
				 }
				@Override
				public void onStartTrackingTouch(SeekBar arg0)
				{
				}

				@Override
				public void onStopTrackingTouch(SeekBar arg0)
				{
				}
        };
		seekBar2.setOnSeekBarChangeListener(l2);
		 
		final Handler trHandler = new Handler();
		Thread t = new Thread()     // UDP sending thread
        {
	            public void run()
                {
	        		// send UDP broadcast with params
	            	while (threadsalive)
	            	{
    	            	if (dataready)
	                 	{
     	           		  try
                          {
                                // sending to everyone on the subnet (broadcast)
	           			        DatagramPacket packet = new DatagramPacket(sendbuf, BUFSIZE,InetAddress.getByAddress(bcastadr), 4791);
	           					sockets.send(packet);			
	           				    trHandler.post(new Runnable()
                                {
	           				    	@Override
	           				    	public void run()   // char animation update on every packet sent
                                    {
	           				    		String s;
	           				    		s=(String) textView.getText();
	           				    		s=s.substring(0,s.length()-1);
	           				    		s+=anime[animeo];           				    		
	           				    		textView.setText(s);
	           				    		animeo++;
	           				    		if (animeo==4) animeo=0;
	           			            }
	           			        });
	           					
	           		 	  }
                          catch (SocketException e)
                          {
//						   		statustext.setText(e.getMessage());
                                Log.e("QCSV",e.getMessage());
	           					e.printStackTrace();
	           			  }
                          catch (IOException e)
                          {
//						   		statustext.setText(e.getMessage());
                                Log.e("QCSV",e.getMessage());
	           				    e.printStackTrace();
	           		      }
                          dataready=false;
	            	              	
	            	      try
                          {
	            	    		Thread.sleep(fq);
					      }
                          catch (InterruptedException e)
                          {
//						   		statustext.setText(e.getMessage());			
					    		e.printStackTrace();
					      }
	            		}
	            	}
	            }
	    };
	    threadsalive=true;
		t.setPriority((Thread.MAX_PRIORITY + Thread.NORM_PRIORITY)/2);
	    t.start();
	     
	    Thread tr = new Thread()       // UDP receiving thread
        {
	            public void run()
                {
	            	while (threadralive)
	            	{
  	           		  try
                      {
	           			    DatagramPacket packet = new DatagramPacket(receivebuf, BUFSIZE);
						    Arrays.fill(receivebuf,(byte)0);
                            sockets.receive(packet);
	           				if ((receivebuf[0]=='Q') && (receivebuf[1]=='C') && (receivebuf[2]=='S') && (receivebuf[3]=='V') && (receivebuf[4]=='A'))
                            {
	           					rRoll     = (short) (((receivebuf[5] << 8) & 0x0000ff00) | (receivebuf[6] & 0x000000ff));
	           					rPitch    = (short) (((receivebuf[7] << 8) & 0x0000ff00) | (receivebuf[8] & 0x000000ff));
	           					rHDG      = (short) (((receivebuf[9] << 8) & 0x0000ff00) | (receivebuf[10] & 0x000000ff));
	           					rALT      = (short) (((receivebuf[11]<< 8) & 0x0000ff00) | (receivebuf[12] & 0x000000ff));
                                BatLvl    = (short) (((receivebuf[15]<< 8) & 0x0000ff00) | (receivebuf[16] & 0x000000ff));
           						rThrottle = (short) (receivebuf[13] & 0x000000ff);
	           					WifiAtt   = (short) (receivebuf[14] & 0x000000ff);
	           					rYaw      =  (byte) (receivebuf[17] & 0x000000ff);
	           					rCtrl     =  (byte) (receivebuf[18] & 0x000000ff);
								rLoopTime =  (byte) (receivebuf[19] & 0x000000ff);
	           				    trHandler.post(new Runnable()
                                {
	           				    	@Override
	           				    	public void run()
                                    {
	           				    		String s="  DRONE:\nBank=";
	           				    		s+=Integer.toString(rRoll-90);		 
	           				    		s+="\nPitch=";
	           				    		s+=Integer.toString(rPitch-90);				 
	           				    		/*s+="\nHDG=";
	           				    		s+=Short.toString(rHDG);				 
	           				    		s+="\nALT=";
	           				    		s+=Short.toString(rALT);*/
	           				    		s+="\nTHR=";
	           				    		s+=Short.toString((short)(rThrottle*100/255));
	           				    		s+="%";
	           				    		s+="\nBAT=";
	           				    		s+=Short.toString(BatLvl);
	           				    		s+="mV";
	           				    		s+="\nCTRL=";
	           				    		if (rCtrl==0) s+="OFF";
	           				    		if (rCtrl==1) s+="ON";
	           				    		s+="\nLoop(ms)=";
										s+=Integer.toString(rLoopTime);
										s+="\n";
	           				    		s+=anime[animei];        				    		
	           				    		dronedata.setText(s);
	           				    		animei++;
	           				    		if (animei==4) animei=0;
	           			            }
	           			        });
                            }
                      }
                      catch (SocketException e)
                      {
//						   		statustext.setText(e.getMessage());
	           					e.printStackTrace();
	           		  }
                      catch (IOException e)
                      {
//						   		statustext.setText(e.getMessage());
						  		e.printStackTrace();
					  }
                      catch (Exception e)
                      {
//						   		statustext.setText(e.getMessage());
						  		e.printStackTrace();
          		      }
                    }
	            }
	    };
	    tr.start();
		threadralive=true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
    {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
    {
		int id = item.getItemId();
		if (id == R.id.action_settings)
        {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onDestroy()
    {
		threadsalive=false;
		threadralive=false;		
		sockets.close();		
		super.onDestroy();
	}

	public void UpdatePIDValues (View v)
    {
		Kp=Float.valueOf(editText1.getText().toString());
		Kd=Float.valueOf(editText5.getText().toString());
		Ki=Float.valueOf(editText4.getText().toString());
		BKp=ByteBuffer.allocate(Float.SIZE).putFloat(Kp).array();
		BKd=ByteBuffer.allocate(Float.SIZE).putFloat(Kd).array();
		BKi=ByteBuffer.allocate(Float.SIZE).putFloat(Ki).array();
	}
}
