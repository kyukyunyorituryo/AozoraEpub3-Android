package io.github.kyukyunyorituryo.aozoraepub3.info;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 画像情報
 * Velocity内でも利用するための情報も格納する */
public class ImageInfo
{
	/** ファイルのID 0001 */
	String id;
	/** 出力ファイル名 拡張子付き 0001.png */
	String outFileName;
	/** 画像フォーマット png jpg gif */
	String ext;
	
	/** 画像幅 */
	int width = -1;
	/** 画像高さ */
	int height = -1;
	
	/** 出力画像幅 */
	int outWidth = -1;
	/** 出力画像高さ */
	int outHeight = -1;
	
	/** カバー画像ならtrue */
	boolean isCover;
	
	/** 回転角度 右 90 左 -90 */
	public int rotateAngle = 0;
	
	/** 画像の情報を生成
	 * @param ext png jpg gif の文字列 */
	public ImageInfo(String ext, int width, int height)
	{
		super();
		this.ext = ext.toLowerCase();
		this.width = width;
		this.height = height;
	}
	/** ファイルから画像情報を生成 */
	static public ImageInfo getImageInfo(File imageFile) throws IOException
	{
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(imageFile));
		ImageInfo imageInfo = ImageInfo.getImageInfo(bis);
		bis.close();
		return imageInfo;
	}
	/** 画像ストリームから画像情報を生成
	 * @throws IOException */
	public static ImageInfo getImageInfo(InputStream is) {
		if (is == null) return null;

		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true; // 実際に画像を読み込まず情報だけ取得

			BitmapFactory.decodeStream(is, null, options);

			if (options.outWidth > 0 && options.outHeight > 0) {
				String ext = getImageExtension(options.outMimeType);
				return new ImageInfo(ext, options.outWidth, options.outHeight);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * MIMEタイプから拡張子を取得
	 */
	private static String getImageExtension(String mimeType) {
		if (mimeType == null) return "unknown";
		if (mimeType.equals("image/jpeg")) return "jpg";
		if (mimeType.equals("image/png")) return "png";
		if (mimeType.equals("image/webp")) return "webp";
		return "unknown";
	}
	/** Uri から画像情報を取得 (Android用)
	 * @param context Androidの `Context`
	 * @param imageUri 画像の `Uri`
	 */
	public static ImageInfo getImageInfo(Context context, Uri imageUri) throws IOException {
		ContentResolver resolver = context.getContentResolver();
		InputStream is = resolver.openInputStream(imageUri);
		if (is == null) {
			throw new IOException("Failed to open InputStream from Uri");
		}
		return getImageInfo(is, getFileExtension(imageUri.toString()));
	}

	/** InputStream から画像情報を取得 */
	private static ImageInfo getImageInfo(InputStream is, String ext) throws IOException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true; // 画像のデコードはせず、サイズのみ取得
		BitmapFactory.decodeStream(is, null, options);
		is.close();
		return new ImageInfo(ext, options.outWidth, options.outHeight);
	}
	/** ファイル名やパスから拡張子を取得 */
	private static String getFileExtension(String filename) {
		int lastDot = filename.lastIndexOf('.');
		if (lastDot == -1) return "";
		return filename.substring(lastDot + 1).toLowerCase();
	}
	/**
	 * Bitmap から画像情報を取得
	 *
	 * @param ext   画像の拡張子
	 * @param image 画像 (Bitmap)
	 * @return 画像情報 (拡張子, 幅, 高さ)
	 */
	public static ImageInfo getImageInfo(String ext, Bitmap image) {
		if (image == null) return null;
		return new ImageInfo(ext, image.getWidth(), image.getHeight());
	}
	
	public String getId()
	{
		return id;
	}
	
	public void setId(String id)
	{
		this.id = id;
	}
	
	public String getOutFileName()
	{
		return outFileName;
	}
	
	public void setOutFileName(String file)
	{
		this.outFileName = file;
	}
	
	public void setExt(String ext)
	{
		this.ext = ext;
	}
	public String getExt()
	{
		return this.ext;
	}
	/** mime形式(image/png)の形式フォーマット文字列を返却 */
	public String getFormat()
	{
		return "image/"+(this.ext.equals("jpg")?"jpeg":this.ext);
	}
	
	public boolean getIsCover()
	{
		return this.isCover;
	}
	
	public void setIsCover(boolean isCover)
	{
		this.isCover = isCover;
	}
	public int getWidth()
	{
		return width;
	}
	public void setWidth(int width)
	{
		this.width = width;
	}
	public int getHeight()
	{
		return height;
	}
	public void setHeight(int height)
	{
		this.height = height;
	}
	
	public int getOutWidth()
	{
		return outWidth;
	}
	public void setOutWidth(int outWidth)
	{
		this.outWidth = outWidth;
	}
	public int getOutHeight()
	{
		return outHeight;
	}
	public void setOutHeight(int outHeight)
	{
		this.outHeight = outHeight;
	}
}
