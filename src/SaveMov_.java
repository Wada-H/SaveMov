import hw.savemov.SaveMov;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;
import ij.plugin.Animator;
import ij.plugin.PlugIn;

import javax.swing.*;
import java.awt.*;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
//import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_H264; //1.44
//import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_MPEG4; //1.44


//// Save as Movei �̏k���B h.264�R�[�f�B�b�N�g�p��mov�t�@�C���Ɍ��肷��Bbitrate���傫�߂ōs���B 20150319 hwada

/// ���m�̖��
/// 20150403 �摜�̈Ӑ}���Ȃ��g�����N����B�� : 153x145 -> 160 x 145�ɂȂ��Ĉ������΂����B
///	->���̖��͌���FFmepgFrameRecoder�ɂ����āAwidth��16�̔{���ɂ���悤�Ɍv�Z���Ă��邱�Ƃ����������B�����炭���̌�̃G���R�[�h�ɂ�����16�{�ł��邱�ƂɈӖ�������悤���B
///	�@����ɑ΂��āA�����瑤�ŁAwidth��16�̔{���Ƃ��āA�]�����������邱�Ƃŉ�����_���B
/// 20151016 ���ꂩ��̎�����ӂ݂�WebM�������Ή����邩�H ->�g���q.webm,codec VP8(141),VP9(169)
///	->�ʃv���O�C���Ƃ��ĕ������悤�B
/// 20151021 SaveWebM�J�����ɋC����������
///	javacv,ffmpeg����API�ɂ����Ĉꕔ�A�\���⃁�\�b�h���ύX�ɂȂ��Ă���B
///	��̓I�ɂ� IplImage.createFrom(BufferedImage);���p�~����Ă���BBufferedImage->Frame�ցB���̉ߒ��ɂ�����ARGB->RGBA�ւ̕ϊ��K�{�B
/// version 20151021
/// 20151127 ���t�@�N�^�����O�J�n
/// version 20151218 SaveMov.java�Ƃ��ăN���X��������
/// javacv1.2�Ή��H
/// 20170327 SaveMP4�쐬���ɔ��������s������P SaveMov.java�̕ύX�̂�
/// 20171018 ���������}�C�i�X��Timestamp�v������B�X�^�[�g�|�W�V���������߂鍀�ڂ�ǉ����邱�ƂŒB������悤�l����B->o.k
/// 20180608 �ꕔ�̏���(�����������݂ƃG���R�[�h����)����񉻂��邱�ƂŎ኱�̑��xup
/// 20190405 javacv144�ɂ����ĕs����o�� ->���̊��ł͂łȂ��H -> ver.20180608�Ŗ��Ȃ�����
/// 20190829 �g�c���v�]�̕��\�L�̃^�C���X�^���v�@�\�̒ǉ�
/// 20190906 javacv151�ŕs����o��B ->�ǂ������̃R�[�h���}���`�X���b�h�������悤�ł��̉e���Ƃ�������B�����炪�V���O���L�q����΂����Ɠ������߁B
/// 20191017 �O��C���ɂ����đO�X��ǉ�(���\��)�����f����Ȃ��s��� -> �V���O���X���b�h�̃��\�b�h�ɒǉ��ŉ��
/// 20200227 ����쐬����ROI���������ދ@�\�������Ȃ����B�܂��A�X�P�[����^�C���X�^���v�����������_��ɂł��Ȃ���(sec, micron�Œ�ł��邽��)
/// 20200228 �Ƃ肠�����AROI���������ނ悤�ɕύX
/// 20201007 javacv1.54�ɂ�����h264�g�p���ɒv���I�G���[�ŗ�����B
/// 20201012 ��L���̓f�t�H���g�g�p��libx246�R����Mac����̂悤�ł���B���L�R�[�h�łƂ肠��������\�Brecorder.setVideoCodecName("libopenh264"); // suggesting by saudet(javacv author?)
/// 20201013 mpeg4�I�����Awindows�œ����Ȃ�

public class SaveMov_ implements PlugIn {

	SaveMov savemov;

	String version = "20201012"; //with libx264 on Mac �΍�
	//String version = "20200228";
	//String version = "20191017"; //javacv 1.51
	//String version = "20180829"; //javacv 1.44
	String[] codecs = {"H.264","MPEG4"};
    int[] codecsID = {AV_CODEC_ID_H264, AV_CODEC_ID_MPEG4};
    String[] codecNames = {"libopenh264", "mpeg4"};

    
    //int codecsID = 28; //h264;
    //int codecsID = 13; //mpeg4
    int codec = 0;
    
    String dir, file, title;
    double fps = 10;
    int br = 5000;
    int width, height;
    String ct = "mov";

    double interval = 1.0;
    double scale = 0.0;
    int scale_pix_size = 0;
    ImagePlus imp = null;

    int time_stamp_size = 20;

    
    ///// dialog�p ////
    Panel gd_panel;
    JPanel cordec_panel;
    JLabel framerate_label;
    JTextField framerate;
    JLabel bitrate_label;
    JTextField bitrate;
    JLabel cordec_label;
    JComboBox<String> cordec_box;
    JPanel timestamp_panel;
    JCheckBox timestamp_cb;
	JCheckBox fixMinCheckbox;
    JLabel timestamp_label;
    JTextField timestamp;
    JLabel zeroposition_label;
    JTextField zeroposition;
    JPanel scale_panel;
    JCheckBox scale_cb;
    JLabel scale_label;
    JTextField scalebar;
    JLabel stampsize_label;
    JTextField stampsize;
    JCheckBox expand_cb;
    //////////////////
    
    
    
    
	  public void run( String arg ) {

	        
		  
		    //���݂̃C���[�W�̎擾
		    imp = WindowManager.getCurrentImage();
		    if (imp == null) {
		      IJ.noImage();
		      return;
		    }
		    
	        savemov = new SaveMov(imp);


            if(imp.getOriginalFileInfo() != null){ //�V����������摜�ɂ�FileInfo���ݒ肳��Ă��Ȃ�
    			dir = imp.getOriginalFileInfo().directory; //�����l���I���W�i���t�@�C���Ɠ����ꏊ�ɁB
    		}else{
    			dir = "home";
    		}
            
            
	        if(showDialog() != true){ //�_�C�A���O���J���ď������I����true���Ԃ�悤�ɐ݌v���Ă���B
	        	return;
	        }

	        savemov.record();
	        //savemov.recordMulti();//javacv1.44�܂�, 1.51�ł̓}���`�����Ă���̂ŋt�ɃG���[���o�Ă��܂�
		  	//savemov.testrecord(); //javacv1.5.4�ɂ�����h.264�g�p���N���b�V�����邽�߁AGithub��javacv:issues�ɖ₢���킹���ۂɎg�p

		  	savemov.showMovieImage();
	        	        
	        
	  }

	    public boolean showDialog() {
	    	
	        
	        GenericDialog gd = new GenericDialog("SaveMov ver." + version, IJ.getInstance());
	        fps = Animator.getFrameRate();
	        br = savemov.getDefaultBitrate();
	    	interval = imp.getFileInfo().frameInterval; //sec
	    	title = imp.getTitle();
	    	
	    	FlowLayout gd_layout = new FlowLayout();
	    	gd_layout.setAlignment(FlowLayout.RIGHT);
	    	
	    	gd_panel = new Panel(gd_layout); ///Panel�łȂ��ƃ{�^���������Ȃ��Ȃ�A�A�A�A
	    	gd_panel.setPreferredSize(new Dimension(320, 250));
	    	/////////////// cordec panel
	    	cordec_panel = new JPanel((new GridLayout(3,2)));
	    	cordec_panel.setAlignmentX(JPanel.BOTTOM_ALIGNMENT);
	    	
	    	framerate_label = new JLabel("Frame rate(f/s)");
	    	framerate_label.setVerticalAlignment(JLabel.CENTER);
	    	framerate_label.setHorizontalAlignment(JLabel.CENTER);
	    	
	    	framerate = new JTextField(String.valueOf(fps), 1);
	
	    	bitrate_label = new JLabel("Bit rate(kb/s)");
	    	bitrate_label.setVerticalAlignment(JLabel.CENTER);
	    	bitrate_label.setHorizontalAlignment(JLabel.CENTER);
	    	bitrate = new JTextField(String.valueOf(br), 0);
	    	
	    	cordec_label = new JLabel("Cordec");
	    	cordec_label.setVerticalAlignment(JLabel.CENTER);
	    	cordec_label.setHorizontalAlignment(JLabel.CENTER);
	    	cordec_box = new JComboBox<String>();
	    	cordec_box.addItem(codecs[0]);
	    	cordec_box.addItem(codecs[1]);

	    	cordec_panel.add(framerate_label);
	    	cordec_panel.add(framerate);
	    	cordec_panel.add(bitrate_label);
	    	cordec_panel.add(bitrate);
	    	cordec_panel.add(cordec_label);
	    	cordec_panel.add(cordec_box);
	    	
	    	
	    	/////////////// timestamp_panel
	    	JPanel timestamp_panel = new JPanel(new GridLayout(3,2));
	    	timestamp_panel.setPreferredSize(new Dimension(350, 75));
	    	
	    	timestamp_cb = new JCheckBox("Add time stamp:");
	    	timestamp_cb.setHorizontalAlignment(JCheckBox.RIGHT);
	    	timestamp_cb.setSelected(true);
	    	
	    	JPanel timestamp_p = new JPanel(new GridLayout(1,2));
	    	timestamp_label = new JLabel("Interval(sec)");
	    	timestamp_label.setVerticalAlignment(JLabel.CENTER);
	    	timestamp_label.setHorizontalAlignment(JLabel.CENTER);
	    	timestamp = new JTextField(String.valueOf(interval), 4); //�~���Z�J���h�I�[�_�[�ɂ��Ή����B

	    	timestamp_p.add(timestamp_label);
	    	timestamp_p.add(timestamp);
	    	
	    	JPanel zeroposition_p = new JPanel(new GridLayout(1,2));
	    	zeroposition_label = new JLabel("ZeroPosition");
	    	zeroposition_label.setVerticalAlignment(JLabel.CENTER);
	    	zeroposition_label.setHorizontalAlignment(JLabel.CENTER);
	    	zeroposition = new JTextField(String.valueOf(imp.getCurrentSlice()), 4); //�J�����gslice��t�������̂��ǂ����H
	    	zeroposition_p.add(zeroposition_label);
	    	zeroposition_p.add(zeroposition);
	    	
	    	JPanel stampsize_p = new JPanel(new GridLayout(1,2));
	    	stampsize_label = new JLabel("Font size:");
	    	stampsize_label.setVerticalAlignment(JLabel.CENTER);
	    	stampsize_label.setHorizontalAlignment(JLabel.CENTER);
	    	stampsize = new JTextField(String.valueOf(time_stamp_size), 4);
	    	
	    	stampsize_p.add(stampsize_label);
	    	stampsize_p.add(stampsize);
	    		    	
	    	JPanel fixMinPanel = new JPanel(new GridLayout(1,1));
			fixMinCheckbox = new JCheckBox("Minute stamp");
			fixMinCheckbox.setHorizontalAlignment(JCheckBox.RIGHT);
			fixMinCheckbox.setSelected(false);

			fixMinPanel.add(fixMinCheckbox);

	    	timestamp_panel.add(timestamp_cb);
	    	timestamp_panel.add(timestamp_p);
	    	timestamp_panel.add(fixMinPanel);
	    	timestamp_panel.add(zeroposition_p);
	    	timestamp_panel.add(new Label("")); //��̃��x��
	    	timestamp_panel.add(stampsize_p);

	    	
	    	/////////////// scale_panel
	    	
	    	JPanel scale_panel = new JPanel(new GridLayout(2,2));
	    	scale_panel.setPreferredSize(new Dimension(350, 50));
	    	
	    	expand_cb = new JCheckBox("Height expansion");
	    	expand_cb.setHorizontalAlignment(JCheckBox.RIGHT);
	    	expand_cb.setSelected(true);

	    	scale_cb = new JCheckBox("Add scale bar:");
	    	scale_cb.setHorizontalAlignment(JCheckBox.RIGHT);
	    	scale_cb.setSelected(true);
	    	
	    	JPanel scale_p = new JPanel(new GridLayout(1,2));
	    	scale_label = new JLabel("Scale bar(um)");
	    	scale_label.setVerticalAlignment(JLabel.CENTER);
	    	scale_label.setHorizontalAlignment(JLabel.CENTER);
	    	
	    	scalebar = new JTextField("10", 4);

	    	scale_p.add(scale_label);
	    	scale_p.add(scalebar);

	    	JPanel scale_note_p = new JPanel(new GridLayout(1,1));
	    	JLabel scale_note = new JLabel("*Check 'Set Scale'");
	    	scale_note.setHorizontalAlignment(JLabel.RIGHT);
	    	scale_note_p.add(scale_note);
	    	
	    	scale_panel.add(scale_cb);
	    	scale_panel.add(scale_p);
	    	scale_panel.add(expand_cb);
	    	scale_panel.add(scale_note_p);
	    	
	    	/////////////// add dialog
	    	gd_panel.add(cordec_panel);
	    	gd_panel.add(timestamp_panel);
	    	gd_panel.add(scale_panel);
	    	
	    	gd.addPanel(gd_panel);
	    	
	        gd.showDialog();
	        
	        if (gd.wasCanceled()) {	            
	            return false;
	        }

	        /// �V�����l����������

	        savemov.setFps(Double.valueOf(framerate.getText()));
	        savemov.setBitrate((Integer.valueOf(bitrate.getText()) * 1000));
	        savemov.setCodecId(codecsID[cordec_box.getSelectedIndex()]);
	        savemov.setCodecName(codecNames[cordec_box.getSelectedIndex()]);
	        savemov.setInterval(Double.valueOf(timestamp.getText()));
	        savemov.setScaleValue(Double.valueOf(scalebar.getText()));
	        savemov.setTimeStampSize(Integer.valueOf(stampsize.getText()));
	        savemov.setZeroposition(Integer.valueOf(zeroposition.getText()));
	        savemov.setFixMin(fixMinCheckbox.isSelected());
	        savemov.setAnnotation(scale_cb.isSelected(), timestamp_cb.isSelected());
	        savemov.setExpansion(expand_cb.isSelected());

	        ///
	        SaveDialog sd = new SaveDialog("Save Video", dir, title, "."+ct);
	        if (sd.getDirectory() == null || sd.getFileName() == null) {
	            return false;
	        }
	        //dir = sd.getDirectory(); //save dialog�@���ɑI���������̂ɕύX�B
	        //file = sd.getFileName(); //save dialog�@���ɑI���������̂ɕύX�B

	        savemov.setDir(sd.getDirectory());
	        savemov.setFileName(sd.getFileName());
	        
	        return true;
	        
	    
	    }
	    


}