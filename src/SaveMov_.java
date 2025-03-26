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


//// Save as Movei の縮小。 h.264コーディック使用のmovファイルに限定する。bitrateも大きめで行う。 20150319 hwada

/// 既知の問題
/// 20150403 画像の意図しない拡張が起こる。例 : 153x145 -> 160 x 145になって引き延ばされる。
///	->この問題は元のFFmepgFrameRecoderにおいて、widthを16の倍数にするように計算していることが分かった。おそらくその後のエンコードにおいて16倍であることに意味があるようだ。
///	　これに対して、こちら側で、widthを16の倍数として、余白をもうけることで解消を狙う。
/// 20151016 これからの時代を鑑みてWebM方式も対応するか？ ->拡張子.webm,codec VP8(141),VP9(169)
///	->別プラグインとして分離しよう。
/// 20151021 SaveWebM開発中に気がついたこと
///	javacv,ffmpeg等のAPIにおいて一部、構造やメソッドが変更になっている。
///	具体的には IplImage.createFrom(BufferedImage);が廃止されている。BufferedImage->Frameへ。この過程においてARGB->RGBAへの変換必須。
/// version 20151021
/// 20151127 リファクタリング開始
/// version 20151218 SaveMov.javaとしてクラス分け完了
/// javacv1.2対応？
/// 20170327 SaveMP4作成中に発見した不具合を改善 SaveMov.javaの変更のみ
/// 20171018 小椋氏よりマイナスのTimestamp要請あり。スタートポジションを決める項目を追加することで達成するよう考える。->o.k
/// 20180608 一部の処理(文字書き込みとエンコード部分)を並列化することで若干の速度up
/// 20190405 javacv144において不具合が出る ->私の環境ではでない？ -> ver.20180608で問題なく動く
/// 20190829 吉田氏要望の分表記のタイムスタンプ機能の追加
/// 20190906 javacv151で不具合が出る。 ->どうも元のコードがマルチスレッド化したようでその影響とおもわれる。こちらがシングル記述すればちゃんと動くため。
/// 20191017 前回修正において前々回追加(分表示)が反映されない不具合報告 -> シングルスレッドのメソッドに追加で回避
/// 20200227 動画作成時にROIを書き込む機能をつけられないか。また、スケールやタイムスタンプをもう少し柔軟にできないか(sec, micron固定であるため)
/// 20200228 とりあえず、ROIを書き込むように変更
/// 20201007 javacv1.54においてh264使用時に致命的エラーで落ちる。
/// 20201012 上記問題はデフォルト使用のlibx246由来でMac限定のようである。左記コードでとりあえず回避可能。recorder.setVideoCodecName("libopenh264"); // suggesting by saudet(javacv author?)
/// 20201013 mpeg4選択時、windowsで動かない

public class SaveMov_ implements PlugIn {

	SaveMov savemov;

	String version = "20201012"; //with libx264 on Mac 対策
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

    
    ///// dialog用 ////
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

	        
		  
		    //現在のイメージの取得
		    imp = WindowManager.getCurrentImage();
		    if (imp == null) {
		      IJ.noImage();
		      return;
		    }
		    
	        savemov = new SaveMov(imp);


            if(imp.getOriginalFileInfo() != null){ //新しく作った画像にはFileInfoが設定されていない
    			dir = imp.getOriginalFileInfo().directory; //初期値をオリジナルファイルと同じ場所に。
    		}else{
    			dir = "home";
    		}
            
            
	        if(showDialog() != true){ //ダイアログを開いて処理が終わればtrueが返るように設計してある。
	        	return;
	        }

	        savemov.record();
	        //savemov.recordMulti();//javacv1.44まで, 1.51ではマルチ化しているので逆にエラーが出てしまう
		  	//savemov.testrecord(); //javacv1.5.4においてh.264使用時クラッシュするため、Githubのjavacv:issuesに問い合わせた際に使用

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
	    	
	    	gd_panel = new Panel(gd_layout); ///Panelでないとボタンが効かなくなる、、、、
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
	    	timestamp = new JTextField(String.valueOf(interval), 4); //ミリセカンドオーダーにも対応を。

	    	timestamp_p.add(timestamp_label);
	    	timestamp_p.add(timestamp);
	    	
	    	JPanel zeroposition_p = new JPanel(new GridLayout(1,2));
	    	zeroposition_label = new JLabel("ZeroPosition");
	    	zeroposition_label.setVerticalAlignment(JLabel.CENTER);
	    	zeroposition_label.setHorizontalAlignment(JLabel.CENTER);
	    	zeroposition = new JTextField(String.valueOf(imp.getCurrentSlice()), 4); //カレントsliceかtがいいのかどっち？
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
	    	timestamp_panel.add(new Label("")); //空のラベル
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

	        /// 新しい値を代入し直し

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
	        //dir = sd.getDirectory(); //save dialog　中に選択したものに変更。
	        //file = sd.getFileName(); //save dialog　中に選択したものに変更。

	        savemov.setDir(sd.getDirectory());
	        savemov.setFileName(sd.getFileName());
	        
	        return true;
	        
	    
	    }
	    


}