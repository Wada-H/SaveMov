package hw.savemov;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.Animator;
import ij.plugin.CanvasResizer;
import ij.plugin.RGBStackConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

//import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_YUV420P; //1.44

public class SaveMov {
	// version 20190906 javacv 1.51//

	// version 20170327 javacv 1.44//
	
	ImagePlus imp;
	ImageStack f_stack_img;
	ImageStack stack_img;
		
	int width;
	int height;
	
	int expand_width;
	int expand_height;
	
	
	/// scale setteing ///
	boolean scale;
	double x_scale;
	int scale_pix_size;
	
	/// expand image setting ///
	boolean expand = true;
	int time_height;
	int scale_height;
	int exHeight;
	int scale_pix_height;
	
	/// time stamp setting ///
	boolean time_stamp;
	int time_stamp_size;
	int zeroposition;
	boolean fixMin;
	
	/// record setting ///
	String dir;
	String file;
	
	int br;
	double fps;
	int codec;
	double interval;
	String codecName = "libopenh264";

    String ct = "mov";

	
	public SaveMov(ImagePlus img){
		imp = img;
		width = img.getWidth();
		height = img.getHeight();
		expand_width = width;
		expand_height = height;
				
    	time_height = -20; //scalebar の書き込み位置補正のため
    	scale_pix_height = 2;
    	scale_height = 0;
    	exHeight = 0;
	}
	
	public void setFileName(String f){
		file = f;
	}
	
	public void setDir(String d){
		dir = d;
	}
	
	public void setFps(double f){
		fps = f;
	}
	
	public void setBitrate(int b){
		br = b;
	}
	
	public void setCodecId(int c){
		codec = c;
	}
	public void setCodecName(String cName){
		codecName = cName;
	};
	
	public void setInterval(double sec){
		interval = sec;
	}
	
	
	public void setTimeStampSize(int size){
		time_stamp_size = size;
		time_height = time_stamp_size + 5;
	}
	
	public void setScaleValue(double scale_value){
    	x_scale = imp.getFileInfo().pixelWidth; // um/pixel
    	scale_pix_size = (int)Math.round((scale_value / x_scale));
    	scale_pix_height = 2;
    	scale_height = scale_pix_height + 5;
	}
	
	
	public void setExpansion(boolean b){
		expand = b;
	}
	
	public void setZeroposition(int p) {
		zeroposition = p;
	}

	public void setFixMin(boolean b){
		fixMin = b;
	}
	
    private ImageStack canvasResize(ImageStack stack_img){
    	ImageStack return_stack;
    	
		CanvasResizer cr = new CanvasResizer();
    		
    	exHeight = time_height + scale_height;
    	return_stack = new ImageStack(width,(height + exHeight)); //リセット
    	return_stack = cr.expandStack(stack_img, width, (height + exHeight) , 0, time_height);
    	expand_width = return_stack.getWidth();
    	expand_height = return_stack.getHeight();
    	

    	return return_stack;
    }

    private ImageStack expandForMovie(ImageStack stack_img){
    	ImageStack return_stack;
    	return_stack = stack_img;
		CanvasResizer cr = new CanvasResizer();
    	if(expand == true){
    		return_stack = this.canvasResize(stack_img);
    	}

    	int new_width = (int)(Math.ceil(expand_width / 16.0) * 16);
    	//int new_height = stack_img.getHeight();
    	int new_height = (int)(Math.ceil(expand_height / 16.0) * 16); //いつの間にかheightも2倍規制があるようだ

    	int x_p = (new_width - expand_width) / 2 ;
    	int y_p = (new_height - expand_height) / 2;
    	ImageStack buff_stack = return_stack.duplicate();
    	return_stack = cr.expandStack(buff_stack, new_width, new_height ,x_p , y_p);
    	expand_width = return_stack.getWidth();
    	expand_height = return_stack.getHeight();
    	return return_stack;
    	
    }
    
    
    public void showMovieImage(){
    	// 以下　選択できるようにする？
    	ImagePlus m_img = new ImagePlus();
    	m_img.setStack(stack_img);
    	m_img.setTitle("for Movie image");
    	m_img.copyScale(imp);
    	m_img.show();
    }
    
    public void setAnnotation(boolean scale_b, boolean time_b){
    	scale = scale_b;
    	time_stamp = time_b;
    }


    public void record(){
	    long st = System.currentTimeMillis();

    	f_stack_img = getFlattenRGB(imp);//zやchannelはカレントとするか？
    	stack_img = this.expandForMovie(f_stack_img);
    	
        ImageProcessor  ip;
        BufferedImage buffered_img;
	    int t = imp.getNFrames();

    	
    	///stack_imgにscale,intervalを書き込む。

    	//// bitrateを6000k位にすると640x480の画像で白いもやがかかる。->mpeg4だと問題ないのでこのエンコーダーのバグと思われる。

    	//if(stack_img == null){
    	//	
    	//}
    	int offset_t_s_x = (int)(time_stamp_size + 5);
    	int offset_t_s_y = (int)(time_stamp_size + 5);

    	int offset_scale_x = (int)(expand_width - scale_pix_size - 10);
    	int offset_scale_y = (int)(expand_height - scale_height);
    	
    	Roi scale_roi = new Roi(offset_scale_x, offset_scale_y, scale_pix_size, scale_pix_height);//sacalebar h:2px,w:auto
    	Font time_stamp_f = new Font("Arial", Font.PLAIN, time_stamp_size);

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(dir + file, stack_img.getWidth(), stack_img.getHeight());

		recorder.setVideoCodecName(codecName);
    	//recorder.setVideoCodec(codec);
        recorder.setFormat(ct);
        recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(fps);
        recorder.setVideoBitrate(br);

        Java2DFrameConverter j2dfc = new Java2DFrameConverter();


    	try {
			recorder.start();
			for(int i = 1; i <= stack_img.getSize(); i++){

				IJ.showStatus("Encording movie...");
                IJ.showProgress(i+1, t);
            	String time_str = conv_sec_to_time(((i - zeroposition)*interval));

				if(fixMin){
					time_str = convertSecToMin(((i+1 - zeroposition)*interval));
				}

        		ip = stack_img.getProcessor(i);
        		ip.setFont(time_stamp_f);
        		ip.setColor(Color.white);

            	if(scale == true){
            		ip.setAntialiasedText(false);
            		ip.fill(scale_roi);
            	}

            	if(time_stamp == true){
            		ip.setAntialiasedText(true);
            		ip.drawString(time_str, offset_t_s_x, offset_t_s_y); //time stamp
            	}

                buffered_img = ip.getBufferedImage();

                org.bytedeco.javacv.Frame f_img = j2dfc.convert(conv_ARGB_to_RGBA(buffered_img)); //conv_ARGB_to_RGBA()いるのか？しかもtype指定も変な気がする
				//org.bytedeco.javacv.Frame f_img = j2dfc.convert(buffered_img);//これだと背景が赤くなってしまうのでやはり、上記の変換は必須である。

				recorder.record(f_img); //ここでh.264のときクラッシュしている。
				buffered_img.flush();

			}
            recorder.stop();

        }catch(FrameRecorder.Exception err){
            IJ.log("error:" + err);

        }
    	IJ.showStatus(""); //リセット？他に方法がある？
    	long et = System.currentTimeMillis();
    	System.out.println("processTime: " + (et-st) + "msec");
    }


    public void recordMulti(){
        long st = System.currentTimeMillis();

        f_stack_img = getFlattenRGB(imp);//zやchannelはカレントとするか？
        stack_img = this.expandForMovie(f_stack_img);

        int offset_t_s_x = (int)(time_stamp_size + 5);
        int offset_t_s_y = (int)(time_stamp_size + 5);

        int offset_scale_x = (int)(expand_width - scale_pix_size - 10);
        int offset_scale_y = (int)(expand_height - scale_height);

        Font time_stamp_f = new Font("Arial", Font.PLAIN, time_stamp_size);
        Roi scale_roi = new Roi(offset_scale_x, offset_scale_y, scale_pix_size, scale_pix_height);//sacalebar h:2px,w:auto



        ConcurrentHashMap<Integer, org.bytedeco.javacv.Frame> imageMap = new ConcurrentHashMap<>();

        IntStream intStream = IntStream.range(0, stack_img.getSize());
        intStream.parallel().forEach(i ->{

            String time_str = conv_sec_to_time(((i+1 - zeroposition)*interval));
            if(fixMin){
            	time_str = convertSecToMin(((i+1 - zeroposition)*interval));
			}


            ImageProcessor ip = stack_img.getProcessor(i+1);
            ip.setFont(time_stamp_f);
            ip.setColor(Color.white);

            if(scale == true){
                ip.setAntialiasedText(false);
                ip.fill(scale_roi);
            }

            if(time_stamp == true){
                ip.setAntialiasedText(true);
                ip.drawString(time_str, offset_t_s_x, offset_t_s_y); //time stamp
            }

            BufferedImage bufferedImage = ip.getBufferedImage();

            Java2DFrameConverter j2dfc = new Java2DFrameConverter();
            org.bytedeco.javacv.Frame f_img = j2dfc.convert(conv_ARGB_to_RGBA(bufferedImage));
            imageMap.put(i, f_img);
        });

        this.recordHashMap(imageMap);

        long et = System.currentTimeMillis();
        System.out.println("processTime: " + (et-st) + "msec");
    }


    private void recordHashMap(Map<Integer, Frame> imageMap){
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(dir + file, stack_img.getWidth(), stack_img.getHeight());

        recorder.setVideoCodecName(codecName);
        //recorder.setVideoCodec(codec);
        recorder.setFormat(ct);
        recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(fps);
        recorder.setVideoBitrate(br);

        try {
            recorder.start();
            for(int i = 0; i <= imageMap.size(); i++){
                IJ.showStatus("Converting...");
                IJ.showProgress(i+1, imageMap.size());
                recorder.record(imageMap.get(i));
            }
            recorder.stop();

        }catch(FrameRecorder.Exception err){
            IJ.log("error:" + err);

        }
        IJ.showStatus(""); //リセット？他に方法がある？
    }
    
    private ImageStack getFlattenRGB(ImagePlus imp){ //表示しているROIを画像に書き込む(とりあえずすべての画像に)
        
        ImagePlus imp2;
        Overlay OL = imp.getOverlay();
        Roi roi = imp.getRoi();

        ImageStack stk;
        int ss = imp.getNSlices();
        int ff = imp.getNFrames();
        
        
        if(imp.isComposite()){
            imp2 = imp.createHyperStack("", 1, ss, ff, 24);

            new RGBStackConverter().convertHyperstack(imp,imp2);
            if(OL != null){
                imp2.setOverlay(OL);
                IJ.run(imp2, "Flatten", "stack");
            }

            stk = imp2.getStack();

            //とりあえず、ROIはすべて書き込むようにする 20200228
			if(roi != null){
				for(int i = 0; i < stk.getSize(); i++){
					stk.getProcessor(i+1).drawRoi(roi);
				}

			}

            imp2.close();

        }else if (imp.getType()== ImagePlus.COLOR_RGB && OL == null){
            stk = imp.getStack();
        }else{
            imp2 = imp.duplicate();
            new StackConverter(imp2).convertToRGB();
            if(OL != null){
                imp2.setOverlay(OL);
                IJ.run(imp2, "Flatten", "stack");
            }
            stk = imp2.getStack();
            imp2.close();
        }


        return stk;
    }

    public int calcBitrate (int w, int h, double fps, double mFactor){        
        int bitrate = (int)Math.round(w * h * fps * mFactor);
        return bitrate;
    }
    
    public int getDefaultBitrate(){
    	double fps = Animator.getFrameRate();
    	double mFactor = 0.225;
        int bitrate = (int)Math.round(width * height * fps * mFactor) / 1000 * 5;
        return bitrate;
    }
    
    public String conv_sec_to_time(double s){
	    	double sec_decimal = Math.abs(s);
	    	
	    	// Timestampを利用する ->なんかダメっぽい
	    	boolean posiNega = true; //true = positive
	
	    	if(sec_decimal == 0){
	    		return "00:00:00.000";
	    	}
	    	
	    	if(s < 0) {
	    		posiNega = false;
	    	}
	    	
	    	int msec = (int)(sec_decimal % 1 * 1000);
	    	int sec  = (int)(sec_decimal / 1);
	    	int min = sec / 60;
	    	int odd_min = min;
	    	int odd_sec = sec % 60;
	    	int hour = 0;
	    	if(min > 60){
	    		hour = min / 60;
	    		odd_min = min % 60;
	    	}
	    	
	    	String hour_str = String.valueOf(hour);
	    	String odd_min_str = String.valueOf(odd_min);
	    	String odd_sec_str = String.valueOf(odd_sec);
	    	String msec_str = String.valueOf(msec);
	    	
	    	if(hour < 10){
	    		hour_str = "0" + hour_str;
	    	}
	    	
	    	if(odd_min < 10){
	    		odd_min_str = "0" + odd_min_str;
	    	}
	    	
	    	if(odd_sec < 10){
	    		odd_sec_str = "0" + odd_sec_str;
	    	}
	    	
	    	if(msec < 100){
	    		msec_str = "0" + msec_str;
		    	}
	    	
	    	if(msec < 10){
	    		msec_str = "0" + msec_str;
	    	}
	    	
    		String str = hour_str + ":" + odd_min_str + ":" + odd_sec_str + "." + msec_str;;

	    	if(posiNega == false) {
	    		str = "-" + str;
	    	}else {
	    		//str = "+" + str;
	    	}
	    	
	    	return str;
    }

    public String convertSecToMin(double s){
		double sec_decimal = Math.abs(s);

		// Timestampを利用する ->なんかダメっぽい
		boolean posiNega = true; //true = positive

		if(sec_decimal == 0){
			return "000:00.000";
		}

		if(s < 0) {
			posiNega = false;
		}

		int msec = (int)(sec_decimal % 1 * 1000);
		int sec  = (int)(sec_decimal / 1);
		int min = sec / 60;
		int odd_min = min;
		int odd_sec = sec % 60;


		String odd_min_str = String.valueOf(odd_min);
		String odd_sec_str = String.valueOf(odd_sec);
		String msec_str = String.valueOf(msec);


		if(odd_min < 100){
			odd_min_str = "0" + odd_min_str;
		}

		if(odd_min < 10){
			odd_min_str = "0" + odd_min_str;
		}

		if(odd_sec < 10){
			odd_sec_str = "0" + odd_sec_str;
		}

		if(msec < 100){
			msec_str = "0" + msec_str;
		}

		if(msec < 10){
			msec_str = "0" + msec_str;
		}

		String str = odd_min_str + ":" + odd_sec_str + "." + msec_str;;

		if(posiNega == false) {
			str = "-" + str;
		}else {
			//str = "+" + str;
		}

		return str;
	}

    public BufferedImage conv_ARGB_to_RGBA(BufferedImage b_img){
    	BufferedImage new_b_img = null;
    	new_b_img = new BufferedImage(b_img.getWidth(),b_img.getHeight(),BufferedImage.TYPE_INT_ARGB);

    	int width = b_img.getWidth();
    	int height = b_img.getHeight();
    	
    	for(int y = 0; y < height; y++){
    		for(int x = 0; x < width; x++){
    			int rgb_c = b_img.getRGB(x, y);

    			int cA = getA(rgb_c, "argb");
    			int cR = getR(rgb_c, "argb");
    			int cG = getG(rgb_c, "argb");
    			int cB = getB(rgb_c, "argb");

    			new_b_img.setRGB(x, y, rgba(cR, cG, cB, cA));
    			
    		}
    	}	    

    	return new_b_img;
    	
    	
    }
    
    
    public static int getA(int c, String option){
    	int result = 0;
    	if(option == "argb"){
    		result = c>>>24;
    	}else if(option == "rgba"){
    		result = c&0xff;
    	}
    	return result;
    }

    public static int getR(int c, String option){
    	int result = 0;
    	if(option == "argb"){
    		result = c>>16&0xff;
    	}else if(option == "rgba"){
    		result = c>>>24;
    	}
    	return result;
    		    	
    }

    public static int getG(int c, String option){
    	int result = 0;
    	if(option == "argb"){
    		result = c>>8&0xff;
    	}else if(option == "rgba"){
    		result = c>>16&0xff;
    	}
    	return result;	    	    	
    }

    public static int getB(int c, String option){
    	int result = 0;
    	if(option == "argb"){
    		result = c&0xff;
    	}else if(option == "rgba"){
    		result = c>>8&0xff;
    	}
    	
    	return result;
    }

    public static int rgb(int r,int g,int b){
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    public static int argb(int a,int r,int g,int b){
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int rgba(int r, int g, int b, int a){
    	return r << 24 | g << 16 | b << 8 | a;
    }
	
}