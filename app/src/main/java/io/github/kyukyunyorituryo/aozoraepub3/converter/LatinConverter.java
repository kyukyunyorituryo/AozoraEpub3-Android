package io.github.kyukyunyorituryo.aozoraepub3.converter;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import io.github.kyukyunyorituryo.aozoraepub3.util.LogAppender;


/**
 * 「基本ラテン文字のみによる拡張ラテン文字Aの分解表記」の変換クラス
 */
public class LatinConverter
{
	/** 分解表記文字列→拡張ラテン文字の対応テーブル */
	HashMap<String, Character> latinMap = new HashMap<String, Character>();
	/** 分解表記文字列→CIDコードの対応テーブル
	 * int[]{横書き時のグリフのCID, 縦書き時(右90度)のグリフのCID} */
	HashMap<Character, String[]> latinCidMap = new HashMap<Character, String[]>();
	
	public LatinConverter(Context context) throws IOException
	{
		String srcFileName = "chuki_latin.txt";
		AssetManager mngr = context.getAssets();
		InputStream is = mngr.open(srcFileName);
        try (BufferedReader src = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = src.readLine()) != null) {
                lineNum++;
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    try {
                        String[] values = line.split("\t");
                        char ch = values[1].charAt(0);
                        if (!values[0].isEmpty()) latinMap.put(values[0], ch);
                        if (values.length > 3) latinCidMap.put(ch, new String[]{values[2], values[3]});
                    } catch (Exception e) {
                        LogAppender.error(lineNum, srcFileName, line);
                    }
                }
            }
        }
	}
	
	/** 分解表記の文字単体をUTF-8文字に変換 */
    public Character toLatinCharacter(String separated)
	{
		return latinMap.get(separated);
	}
	
	/** 分解表記を含む英字文字列をUTF-8文字列に変換 */
	public String toLatinString(String separated)
	{
		char[] ch = separated.toCharArray();
		char[] out = new char[ch.length];
		Character latin;
		int outIdx = 0;
		for (int i=0; i<ch.length; i++) {
			//2文字か3文字でマッチング
			if (i<ch.length-1) {
				latin = this.latinMap.get(String.valueOf(ch, i, 2));
				if (latin != null) { out[outIdx++] = latin; i++; continue; }
				if (i<ch.length-2) {
					latin = this.latinMap.get(String.valueOf(ch, i, 3));
					if (latin != null) { out[outIdx++] = latin; i+=2; continue; }
				}
			}
			out[outIdx++] = ch[i];
		}
		return new String(out, 0, outIdx);
	}

	/** ラテン文字をグリフタグに変換した文字列に変換 */
	public String toLatinGlyphString(char ch)
	{
		String[] cid = latinCidMap.get(ch);
		if (cid == null) return null;
		StringBuilder buf = new StringBuilder();
		buf.append("<glyph system=\"Adobe-Japan1-6\" code=\"");
		buf.append(cid[0]);
		buf.append("\" v_code=\"");
		buf.append(cid[1]);
		buf.append("\"/>");
		return buf.toString();
	}
}
