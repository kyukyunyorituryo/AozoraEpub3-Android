package io.github.kyukyunyorituryo.aozoraepub3.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Paint;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.zip.ZipOutputStream;


import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import io.github.kyukyunyorituryo.aozoraepub3.info.ImageInfo;
import io.github.kyukyunyorituryo.aozoraepub3.util.LogAppender;

public class ImageUtils
{
	/** 4bitグレースケール時のRGB階調カラーモデル Singleton */
	//static ColorModel GRAY16_COLOR_MODEL;
	/** 8bitグレースケール時のRGB階調カラーモデル Singleton */
	//static ColorModel GRAY256_COLOR_MODEL;

	public static final int NOMBRE_TOP = 1;
	public static final int NOMBRE_BOTTOM = 2;
	public static final int NOMBRE_TOPBOTTOM = 3;

	/** png出力用 */
	//static ImageWriter pngImageWriter;
	/** jpeg出力用 */
	//static ImageWriter jpegImageWriter;

	/** 4bitグレースケール時のRGB階調カラーモデル取得 */
	//static ColorModel getGray16ColorModel()
	/** 8bitグレースケール時のRGB階調カラーモデル取得 */
	//static ColorModel getGray256ColorModel()


	/** ファイルまたはURLの文字列から画像を読み込む
	 * 読み込めなければnull */
	public static Bitmap loadImage(String path) {
		InputStream is = null;
		try {
			if (path.startsWith("http")) {
				// URLから画像を取得
				URL url = new URI(path).toURL();
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setDoInput(true);
				connection.connect();
				is = new BufferedInputStream(connection.getInputStream());
			} else {
				// ローカルファイルから画像を取得
				File file = new File(path);
				if (!file.exists()) return null;
				is = new BufferedInputStream(new FileInputStream(file));
			}
			return BitmapFactory.decodeStream(is);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (is != null) is.close();
			} catch (IOException ignored) { }
		}
	}

	//final static AffineTransform NO_TRANSFORM = AffineTransform.getTranslateInstance(0, 0);
	/** ストリームから画像を読み込み */
	public static Bitmap readImage(String ext, InputStream is) throws IOException {
		try {
			return BitmapFactory.decodeStream(is);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	/** 大きすぎる画像は縮小して出力
	 * @param is 画像の入力ストリーム srcImageがあれば利用しないのでnull
	 * @param srcImage 読み込み済の場合は画像をこちらに設定 nullならisから読み込む
	 * @param zos 出力先Zipストリーム
	 * @param imageInfo 画像情報
	 * @param jpegQuality jpeg画質 (低画質 0.0-1.0 高画質)
	 * @param maxImagePixels 縮小する画素数
	 * @param maxImageW 縮小する画像幅
	 * @param maxImageH 縮小する画像高さ
	 * @param dispW 画面幅 余白除去後の縦横比補正用
	 * @param dispH 画面高さ 余白除去後の縦横比補正用
	 * @param autoMarginLimitH 余白除去 最大%
	 * @param autoMarginLimitV 余白除去 最大%
	 * @param autoMarginWhiteLevel 白画素として判別する白さ 100が白
	 * @param autoMarginPadding 余白除去後に追加するマージン */
	static public void writeImage(InputStream is, Bitmap srcImage, ZipArchiveOutputStream zos, ImageInfo imageInfo,
								  float jpegQuality, ColorMatrix gammaMatrix, int maxImagePixels, int maxImageW, int maxImageH, int dispW, int dispH,
								  int autoMarginLimitH, int autoMarginLimitV, int autoMarginWhiteLevel, float autoMarginPadding, int autoMarginNombre, float nombreSize) {
		try {
		String ext = imageInfo.getExt();

		int imgW = imageInfo.getWidth();
		int imgH = imageInfo.getHeight();
		int w = imgW;
		int h = imgH;
		imageInfo.setOutWidth(imgW);
		imageInfo.setOutHeight(imgH);
		//余白チェック時に読み込んだ画像のバッファ
		byte[] imgBuf = null;

		//回転とコントラスト調整なら読み込んでおく
		if (srcImage == null && (imageInfo.rotateAngle != 0 || gammaMatrix != null)) srcImage = readImage(ext, is);

		int[] margin = null;
		if (autoMarginLimitH > 0 || autoMarginLimitV > 0) {
			int startPixel = (int)(w*0.01); //1%
			int ignoreEdge = (int)(w*0.03); //3%
			int dustSize = (int)(w*0.01); //1%

			//画像がなければ読み込み 変更なしの時にそのまま出力できるように一旦バッファに読み込む
			if (srcImage == null) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				IOUtils.copy(is, baos);
				//is.transferTo(baos);
				imgBuf = baos.toByteArray();
                try (ByteArrayInputStream bais = new ByteArrayInputStream(imgBuf)) {
                    srcImage = readImage(ext, bais);
                }
			}
			margin = getPlainMargin(srcImage, autoMarginLimitH/100f, autoMarginLimitV/100f, autoMarginWhiteLevel/100f, autoMarginPadding/100f, startPixel, ignoreEdge, dustSize, autoMarginNombre, nombreSize);
			if (margin[0]==0 && margin[1]==0 && margin[2]==0 && margin[3]==0) margin = null;
			if (margin != null) {
				//元画像が幅か高さかチェック
				int mw = w-margin[0]-margin[2];
				int mh = h-margin[1]-margin[3];
				double dWH = dispW/(double)dispH;
				double mWH = mw/(double)mh;
				//縦横比で画面の横か縦に合わせる方向が変わらないようにマージンを調整する
				if (w/(double)h < dWH) { //元が縦
					if (mWH > dWH && mw > dispW) { //余白除去で横にはみ出す
						mh = (int)(mw/dWH);
						margin[3] = h-margin[1]-mh;//下マージンを伸ばす
						if (margin[3] < 0) { margin[3] = 0; margin[1] = h-mh; }
					}
				} else { //元が横
					if (mWH < dWH && mh > dispH) { //余白除去で縦にはみ出す
						mw = (int)(mh*dWH);
						double mLR = margin[0]+margin[2];
						margin[0] = (int)((w-mw)*margin[0]/mLR);
						margin[2] = (int)((w-mw)*margin[2]/mLR);
					}
				}
				w = mw;
				h = mh;
			}
		}
		//倍率取得
		double scale = 1;
		if (maxImagePixels >= 10000) scale = Math.sqrt((double)maxImagePixels/(w*h)); //最大画素数指定
		if (maxImageW > 0) scale = Math.min(scale, (double)maxImageW/w); //最大幅指定
		if (maxImageH > 0) scale = Math.min(scale, (double)maxImageH/h); //最大高さ指定

		if (scale >= 1 && (gammaMatrix == null || srcImage.getConfig() == Bitmap.Config.ARGB_8888)) {
			if (srcImage == null) {
				//変更なしならそのままファイル出力
				IOUtils.copy(is, zos);
				//is.transferTo(zos);
			} else {
				if (margin == null && imgBuf != null && imageInfo.rotateAngle==0) {
					//余白除去が無く画像も編集されていなければバッファからそのまま出力
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(imgBuf)) {
                        //bais.transferTo(zos);
						IOUtils.copy(bais, zos);
                    }
				} else {
					//編集済の画像なら同じ画像形式で書き出し 余白があれば切り取る
					if (imageInfo.rotateAngle != 0) {
						Bitmap outImage = Bitmap.createBitmap(h, w, Bitmap.Config.ARGB_8888);
						Canvas canvas = new Canvas(outImage);
						canvas.drawColor(Color.WHITE);
						Matrix matrix = new Matrix();
						int x = 0, y = 0;

						if (imageInfo.rotateAngle == 90) {
							matrix.postRotate(90);
							matrix.postTranslate(0, -srcImage.getHeight());
							if (margin != null) {
								x = -margin[3];
								y = -margin[0];
							}
						} else {
							matrix.postRotate(-90);
							matrix.postTranslate(-srcImage.getWidth(), 0);
							if (margin != null) {
								x = -margin[1];
								y = -margin[2];
							}
						}

						canvas.drawBitmap(srcImage, matrix, null);
						srcImage = outImage;//入れ替え
					} else if (margin != null) {
						srcImage = Bitmap.createBitmap(srcImage, margin[0], margin[1],
								srcImage.getWidth() - margin[2] - margin[0],
								srcImage.getHeight() - margin[3] - margin[1]);
					}

					if (gammaMatrix != null) {
						srcImage = applyColorMatrix(srcImage, gammaMatrix);
					}

					_writeImage(zos, srcImage, ext, jpegQuality);
					imageInfo.setOutWidth(srcImage.getWidth());
					imageInfo.setOutHeight(srcImage.getHeight());

					if (imageInfo.rotateAngle != 0) {
						LogAppender.println("画像回転" + ": " + imageInfo.getOutFileName() + " (" + h + "," + w + ")");
					}
				}
			}
		} else {
			//縮小
			int scaledW = (int)(w*scale+0.5);
			int scaledH = (int)(h*scale+0.5);
			if (imageInfo.rotateAngle != 0) {
				scaledW = (int)(h*scale+0.5);
				scaledH = (int)(w*scale+0.5);
			}
			//画像がなければ読み込み
			if (srcImage == null) srcImage = readImage(ext, is);

			Config imageType = gammaMatrix != null ? Config.ARGB_8888 : srcImage.getConfig();
			Bitmap outImage;

			switch (imageType) {
				case ALPHA_8: // モノクロ（BYTE_BINARY 相当）
					outImage = Bitmap.createBitmap(scaledW, scaledH, Config.ALPHA_8);
					break;
				case RGB_565: // 16bitカラー（BYTE_INDEXED や USHORT_GRAY に相当）
					outImage = Bitmap.createBitmap(scaledW, scaledH, Config.RGB_565);
					break;
				case ARGB_8888: // フルカラー (INT_RGB に相当)
				default:
					outImage = Bitmap.createBitmap(scaledW, scaledH, Config.ARGB_8888);
					break;
			}
			Canvas g = new Canvas(outImage);
			try {
				if (outImage.getConfig() == Bitmap.Config.ALPHA_8 || outImage.getConfig() == Bitmap.Config.RGB_565 || outImage.getConfig() == Bitmap.Config.ARGB_8888) {
					g.drawColor(Color.WHITE);
				}

				Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
				Matrix at = new Matrix();
				at.setScale((float) scale, (float) scale);
				int x = 0, y = 0;

				if (imageInfo.rotateAngle == 0) {
					if (margin != null) {
						x = (int) (-margin[0] * scale + 0.5);
						y = (int) (-margin[1] * scale + 0.5);
					}
				} else if (imageInfo.rotateAngle == 90) {
					at.postRotate(90);
					at.postTranslate(0, -imgH);
					if (margin != null) {
						x = (int) (-margin[3] * scale + 0.5);
						y = (int) (-margin[0] * scale + 0.5);
					}
				} else {
					at.postRotate(-90);
					at.postTranslate(-imgW, 0);
					if (margin != null) {
						x = (int) (-margin[1] * scale + 0.5);
						y = (int) (-margin[2] * scale + 0.5);
					}
				}

				g.drawBitmap(outImage, at, paint);
			} finally {
				// Graphics2D の dispose() 相当の処理は不要
			}
			//ImageIO.write(outImage, imageInfo.getExt(), zos);
			//コントラスト調整
			// ガンマ補正
			if (gammaMatrix != null) {
				Bitmap filteredImage = Bitmap.createBitmap(outImage.getWidth(), outImage.getHeight(), Bitmap.Config.ARGB_8888);
				Canvas g2 = new Canvas(filteredImage);
				Paint paint = new Paint();
				paint.setColorFilter(new ColorMatrixColorFilter(gammaMatrix));
				g2.drawBitmap(outImage, 0, 0, paint);
				outImage = filteredImage;
			}

			// インデックス化・グレースケール変換
			Bitmap filteredImage = null;
			switch (outImage.getConfig()) {
				case ALPHA_8:
					filteredImage = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ALPHA_8);
					break;
				case RGB_565:
					filteredImage = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.RGB_565);
					break;
				case ARGB_8888:
					filteredImage = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888);
					break;
			}

			if (filteredImage != null) {
				Canvas g3 = new Canvas(filteredImage);
				g3.drawBitmap(outImage, 0, 0, null);
				outImage = filteredImage;
			}
			_writeImage(zos, outImage, ext, jpegQuality);
			imageInfo.setOutWidth(outImage.getWidth());
			imageInfo.setOutHeight(outImage.getHeight());
			if (scale < 1) {
				LogAppender.append("画像縮小");
				if (imageInfo.rotateAngle!=0) LogAppender.append("回転");
				LogAppender.println(": "+imageInfo.getOutFileName()+" ("+w+","+h+")→("+scaledW+","+scaledH+")");
			}
			zos.flush();
		}
		} catch (Exception e) {
			LogAppender.println("画像読み込みエラー: "+imageInfo.getOutFileName());
			e.printStackTrace();
		}
	}
	/** 画像をZIPに保存 (マージン指定があればカット) */
	static private void _writeImage(ZipArchiveOutputStream zos, Bitmap srcImage, String ext, float jpegQuality) throws IOException {
		zos.putArchiveEntry(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("image." + ext));

		if ("png".equalsIgnoreCase(ext)) {
			// PNG 出力 (最高画質で圧縮)
			srcImage.compress(CompressFormat.PNG, 100, zos);
		} else if ("jpeg".equalsIgnoreCase(ext) || "jpg".equalsIgnoreCase(ext)) {
			// JPEG 出力 (指定された品質で圧縮)
			srcImage.compress(CompressFormat.JPEG, (int) (jpegQuality * 100), zos);
		} else {
			// デフォルトは PNG
			srcImage.compress(CompressFormat.PNG, 100, zos);
		}

		zos.closeArchiveEntry();
		zos.flush();
	}
	/** PNG画像のエンコーダー（Androidでは不要なのでダミー関数） */
	static private Bitmap.CompressFormat getPngImageWriter() {
		return Bitmap.CompressFormat.PNG;
	}
	/** JPEG画像のエンコーダー（Androidでは `Bitmap.CompressFormat.JPEG` を使用） */
	static private Bitmap.CompressFormat getJpegImageWriter() {
		return Bitmap.CompressFormat.JPEG;
	}
	/** `ColorMatrix` を適用する（ガンマ補正） */
	private static Bitmap applyColorMatrix(Bitmap src, ColorMatrix colorMatrix) {
		Bitmap newBitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(newBitmap);
		android.graphics.Paint paint = new android.graphics.Paint();
		paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
		canvas.drawBitmap(src, 0, 0, paint);
		return newBitmap;
	}
	/** 余白の画素数取得  左右のみずれ調整
	 * @param image 余白を検出する画像
	 * @param limitH 余白のサイズ横 0.0-1.0
	 * @param limitV 余白のサイズ縦 0.0-1.0
	 * @param whiteLevel 余白と判別する白レベル
	 * @param startPixel 余白をチェック開始しする位置 初回が余白なら中へ違えば外が余白になるまで増やす
	 * @param ignoreEdge 行列のチェック時に両端を無視するピクセル数
	 * @param dustSize ゴミのピクセルサイズ
	 * @return 余白画素数(left, top, right, bottom) */
	static private int[] getPlainMargin(Bitmap image, float limitH, float limitV, float whiteLevel, float padding, int startPixel, int ignoreEdge, int dustSize, int nombreType, float nombreSize)
	{
		int[] margin = new int[4]; //left, top, right, bottom
		int width = image.getWidth();
		int height = image.getHeight();

		//rgbともこれより大きければ白画素とする
		int rgbLimit = (int)(256*whiteLevel);

		//余白除去後に追加する余白 (削れ過ぎるので最低で1にしておく)
		int paddingH = Math.max(1, (int)(width*padding));
		int paddingV = Math.max(1, (int)(height*padding));

		//除去制限をピクセルに変更 上下、左右それぞれでの最大値
		int limitPxH = (int)(width*limitH);//後で合計から中央に寄せる
		int limitPxV = (int)(height*limitV)/2;
		//ノンブルがあった場合の最大マージン
		int limitPxT = (int)(height*limitV)/2;
		int limitPxB = (int)(height*limitV)/2;

		if (nombreType == NOMBRE_TOP || nombreType == NOMBRE_TOPBOTTOM) {
			limitPxT += (int)(height*0.05); //5%加算
		}
		if (nombreType == NOMBRE_BOTTOM || nombreType == NOMBRE_TOPBOTTOM) {
			limitPxB += (int)(height*0.05); //5%加算
		}

		int ignoreEdgeR = ignoreEdge;
		//int ignoreEdgeR = (int)(width*0.3); //行の少ないページで問題有り
		//上
		int coloredPixels = getColoredPixelsH(image, width, startPixel, rgbLimit, 0, ignoreEdge, ignoreEdgeR, dustSize);
		if (coloredPixels > 0) {//外側へ
			for (int i=startPixel-1; i>=0; i--) {
				coloredPixels = getColoredPixelsH(image, width, i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, 0);
				margin[1] = i;
				if (coloredPixels == 0) break;
			}
		} else {//内側へ
			margin[1] = startPixel;
			for (int i=startPixel+1; i<=limitPxT; i++) {
				coloredPixels = getColoredPixelsH(image, width, i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, dustSize);
				if (coloredPixels == 0) margin[1] = i;
				else break;
			}
		}
		//下
		coloredPixels = getColoredPixelsH(image, width, height-1-startPixel, rgbLimit, 0, ignoreEdge, ignoreEdgeR, dustSize);
		if (coloredPixels > 0) {//外側へ
			for (int i=startPixel-1; i>=0; i--) {
				coloredPixels = getColoredPixelsH(image, width, height-1-i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, 0);
				margin[3] = i;
				if (coloredPixels == 0) break;
			}
		} else {//内側へ
			margin[3] = startPixel;
			for (int i=startPixel+1; i<=limitPxB; i++) {
				coloredPixels = getColoredPixelsH(image, width, height-1-i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, dustSize);
				if (coloredPixels == 0) margin[3] = i;
				else break;
			}
		}

		//ノンブル除去
		boolean hasNombreT = false;
		boolean hasNombreB = false;
		if (nombreType == NOMBRE_TOP || nombreType == NOMBRE_TOPBOTTOM) {
			//これ以下ならノンブルとして除去
			int nombreLimit = (int)(height * nombreSize)+margin[1];
			int nombreDust = (int)(height * 0.005);
			//ノンブル上
			int nombreEnd = 0;
			for (int i=margin[1]+1; i<=nombreLimit; i++) {
				coloredPixels = getColoredPixelsH(image, width, i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, 0);
				if (coloredPixels == 0) { nombreEnd = i; if (nombreEnd-margin[1] > nombreDust) break; } //ノンブル上のゴミは無視
			}
			if (nombreEnd > margin[1]+height*0.005 && nombreEnd <= nombreLimit) { //0.5%-3％以下
				int whiteEnd = nombreEnd;
				int whiteLimit = limitPxT;//+(int)(height*0.05); //5%加算
				for (int i=nombreEnd+1; i<=whiteLimit; i++) {
					coloredPixels = getColoredPixelsH(image, width, i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, dustSize);
					if (coloredPixels == 0) whiteEnd = i;
					else if (i-nombreEnd > nombreDust) break;
				}
				if (whiteEnd > nombreEnd+height*0.01) { //1%より大きい空白
					margin[1] = whiteEnd;
					hasNombreT = true;
				}
			}
		}
		if (nombreType == NOMBRE_BOTTOM || nombreType == NOMBRE_TOPBOTTOM) {
			//これ以下ならノンブルとして除去
			int nombreLimit = (int)(height * nombreSize)+margin[3];
			int nombreDust = (int)(height * 0.005);
			//ノンブル下
			int nombreEnd = 0;
			for (int i=margin[3]+1; i<=nombreLimit; i++) {
				coloredPixels = getColoredPixelsH(image, width, height-1-i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, 0);
				if (coloredPixels == 0) { nombreEnd = i; if (nombreEnd-margin[3] > nombreDust) break; } //ノンブル下のゴミは無視
			}
			if (nombreEnd > margin[3]+height*0.005 && nombreEnd <= nombreLimit) { //0.5%-3％以下
				int whiteEnd = nombreEnd;
				int whiteLimit = limitPxB;//+(int)(height*0.05); //5%加算
				for (int i=nombreEnd+1; i<=whiteLimit; i++) {
					coloredPixels = getColoredPixelsH(image, width, height-1-i, rgbLimit, 0, ignoreEdge, ignoreEdgeR, dustSize);
					if (coloredPixels == 0) whiteEnd = i;
					else if (i-nombreEnd > nombreDust) break;
				}
				if (whiteEnd > nombreEnd+height*0.01) { //1%より大きい空白
					margin[3] = whiteEnd;
					hasNombreB = true;
				}
			}
		}

		//左右にノンブル分反映
		int ignoreTop = Math.max(ignoreEdge, margin[1]);
		int ignoreBottom = Math.max(ignoreEdge, margin[3]);
		//左
		coloredPixels = getColordPixelsV(image, height, startPixel, rgbLimit, 0, ignoreTop, ignoreBottom, dustSize);
		if (coloredPixels > 0) {//外側へ
			for (int i=startPixel-1; i>=0; i--) {
				coloredPixels = getColordPixelsV(image, height, i, rgbLimit, 0, ignoreTop, ignoreBottom, 0);
				margin[0] = i;
				if (coloredPixels == 0) break;
			}
		} else {//内側へ
			margin[0] = startPixel;
			for (int i=startPixel+1; i<=limitPxH; i++) {
				coloredPixels = getColordPixelsV(image, height, i, rgbLimit, 0, ignoreTop, ignoreBottom, dustSize);
				if (coloredPixels == 0) margin[0] = i;
				else break;
			}
		}
		//右
		coloredPixels = getColordPixelsV(image, height, width-1-startPixel, rgbLimit, 0, ignoreTop, ignoreBottom, dustSize);
		if (coloredPixels > 0) {//外側へ
			for (int i=startPixel-1; i>=0; i--) {
				coloredPixels = getColordPixelsV(image, height, width-1-i, rgbLimit, 0, ignoreTop, ignoreBottom, 0);
				margin[2] = i;
				if (coloredPixels == 0) break;
			}
		} else {//内側へ
			margin[2] = startPixel;
			for (int i=startPixel+1; i<=limitPxH; i++) {
				coloredPixels = getColordPixelsV(image, height, width-1-i, rgbLimit, 05, ignoreTop, ignoreBottom, dustSize);
				if (coloredPixels == 0) margin[2] = i;
				else break;
			}
		}
		//左右のカットは小さい方に合わせる
		//if (margin[0] > margin[2]) margin[0] = margin[2];
		//else margin[2] = margin[0];

		//左右の合計が制限を超えていたら調整
		if (margin[0]+margin[2] > limitPxH) {
			double rate = (double)limitPxH/(margin[0]+margin[2]);
			margin[0] = (int)(margin[0]*rate);
			margin[2] = (int)(margin[2]*rate);
		}
		/*if (margin[1]+margin[3] > limitPxV) {
			double rate = (double)limitPxV/(margin[1]+margin[3]);
			margin[1] = (int)(margin[1]*rate);
			margin[3] = (int)(margin[3]*rate);
		}*/

		//ノンブルがなければ指定値以下にする
		if (!hasNombreT) margin[1] = Math.min(margin[1], limitPxV);
		if (!hasNombreB) margin[3] = Math.min(margin[3], limitPxV);

		//余白分広げる
		margin[0] -= paddingH; if (margin[0] < 0) margin[0] = 0;
		margin[1] -= paddingV; if (margin[1] < 0) margin[1] = 0;
		margin[2] -= paddingH; if (margin[2] < 0) margin[2] = 0;
		margin[3] -= paddingV; if (margin[3] < 0) margin[3] = 0;
		return margin;
	}
	/**
	 * 指定範囲の白くない画素数をカウント
	 *
	 * @param image       比率をチェックする画像
	 * @param w           比率をチェックする幅
	 * @param offsetY     画像内の縦位置
	 * @param rgbLimit    しきい値（これよりも黒い場合カウント）
	 * @param limitPixel  これよりも黒部分が多かったら終了
	 * @param ignoreEdgeL 左端の無視する範囲
	 * @param ignoreEdgeR 右端の無視する範囲
	 * @param dustSize    小さいノイズを無視するためのサイズ
	 * @return 白くないピクセルの数
	 */
	private static int getColoredPixelsH(Bitmap image, int w, int offsetY, int rgbLimit, int limitPixel, int ignoreEdgeL, int ignoreEdgeR, int dustSize) {
		int coloredPixels = 0;

		for (int x = w - 1 - ignoreEdgeR; x >= ignoreEdgeL; x--) {
			int pixel = image.getPixel(x, offsetY);
			if (isColored(pixel, rgbLimit)) {
				// ゴミ(ノイズ)を除外する
				if (dustSize < 4 || !isDust(image, x, image.getWidth(), offsetY, image.getHeight(), dustSize, rgbLimit)) {
					coloredPixels++;
					if (limitPixel < coloredPixels) return coloredPixels;
				}
			}
		}

		return coloredPixels;
	}

	/** 指定範囲の白い画素数の比率を返す
	 * @param image 比率をチェックする画像 (Bitmap)
	 * @param h 比率をチェックする高さ
	 * @param offsetX 画像内の横位置
	 * @param rgbLimit 白と判定するRGB値の閾値
	 * @param limitPixel これよりも白比率が小さくなったら終了 値はlimit+1が帰る
	 * @param ignoreTop 無視する上側の範囲
	 * @param ignoreBottom 無視する下側の範囲
	 * @param dustSize ノイズ除去の閾値
	 * @return 白画素の比率 (0.0 - 1.0)
	 */
	static private int getColordPixelsV(Bitmap image, int h, int offsetX, int rgbLimit, int limitPixel, int ignoreTop, int ignoreBottom, int dustSize) {
		int coloredPixels = 0;

		for (int y = h - 1 - ignoreBottom; y >= ignoreTop; y--) {
			int pixel = image.getPixel(offsetX, y);
			if (isColored(pixel, rgbLimit)) {
				// ゴミ除外
				if (dustSize < 4 || !isDust(image, offsetX, image.getWidth(), y, image.getHeight(), dustSize, rgbLimit)) {
					coloredPixels++;
					if (limitPixel < coloredPixels) return coloredPixels;
				}
			}
		}
		return coloredPixels;
	}
	static boolean isColored(int rgb, int rgbLimit)
	{
		return rgbLimit > (rgb>>16 & 0xFF) || rgbLimit > (rgb>>8 & 0xFF) || rgbLimit > (rgb & 0xFF);
	}

	/**
	 * ゴミ（ノイズ）をチェック
	 *
	 * @param image    対象の画像
	 * @param curX     現在のX座標
	 * @param maxX     画像の最大幅
	 * @param curY     現在のY座標
	 * @param maxY     画像の最大高さ
	 * @param dustSize ノイズとみなす最大サイズ
	 * @param rgbLimit しきい値（これよりも黒い場合ノイズと判定）
	 * @return ゴミ（ノイズ）である場合 `true`
	 */
	public static boolean isDust(Bitmap image, int curX, int maxX, int curY, int maxY, int dustSize, int rgbLimit) {
		if (dustSize == 0) return false;

		// ゴミサイズの縦横2倍の範囲
		int minX = Math.max(0, curX - dustSize - 1);
		maxX = Math.min(maxX, curX + dustSize + 1);
		int minY = Math.max(0, curY - dustSize - 1);
		maxY = Math.min(maxY, curY + dustSize + 1);

		// 縦方向の黒ピクセル数をカウント
		int h = 1;
		for (int y = curY - 1; y >= minY; y--) {
			if (isColored(image.getPixel(curX, y), rgbLimit)) h++;
			else break;
		}
		for (int y = curY + 1; y < maxY; y++) {
			if (isColored(image.getPixel(curX, y), rgbLimit)) h++;
			else break;
		}
		if (h > dustSize) return false;

		// 横方向の黒ピクセル数をカウント
		int w = 1;
		for (int x = curX - 1; x >= minX; x--) {
			if (isColored(image.getPixel(x, curY), rgbLimit)) w++;
			else break;
		}
		for (int x = curX + 1; x < maxX; x++) {
			if (isColored(image.getPixel(x, curY), rgbLimit)) w++;
			else break;
		}
		if (w > dustSize) return false;

		// 左方向チェック
		w = 1;
		for (int x = curX - 1; x >= minX; x--) {
			h = 0;
			for (int y = maxY - 1; y >= minY; y--) {
				if (isColored(image.getPixel(x, y), rgbLimit)) h++;
			}
			if (h > dustSize) return false;
			if (h == 0) break; // 全て白なら終了
			w++;
		}

		// 右方向チェック
		for (int x = curX + 1; x < maxX; x++) {
			h = 0;
			for (int y = maxY - 1; y >= minY; y--) {
				if (isColored(image.getPixel(x, y), rgbLimit)) h++;
			}
			if (h > dustSize) return false;
			if (h == 0) break; // 全て白なら終了
			w++;
		}
		if (w > dustSize) return false;

		// 上方向チェック
		h = 1;
		for (int y = curY - 1; y >= minY; y--) {
			w = 0;
			for (int x = maxX - 1; x >= minX; x--) {
				if (isColored(image.getPixel(x, y), rgbLimit)) w++;
			}
			if (w > dustSize) return false;
			if (w == 0) break; // 全て白なら終了
			h++;
		}

		// 下方向チェック
		for (int y = curY + 1; y < maxY; y++) {
			w = 0;
			for (int x = maxX - 1; x >= minX; x--) {
				if (isColored(image.getPixel(x, y), rgbLimit)) w++;
			}
			if (w > dustSize) return false;
			if (w == 0) break; // 全て白なら終了
			h++;
		}

		return h <= dustSize;
	}

}
