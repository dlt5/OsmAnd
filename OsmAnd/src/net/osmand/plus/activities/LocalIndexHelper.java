package net.osmand.plus.activities;


import android.content.Context;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;

import net.osmand.IndexConstants;
import net.osmand.map.ITileSource;
import net.osmand.map.TileSourceManager;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.SQLiteTileSource;
import net.osmand.plus.download.ui.AbstractLoadLocalIndexTask;
import net.osmand.plus.voice.MediaCommandPlayerImpl;
import net.osmand.plus.voice.TTSCommandPlayerImpl;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;


public class LocalIndexHelper {
		
	private final OsmandApplication app;

	public LocalIndexHelper(OsmandApplication app){
		this.app = app;
	}
	
	
	public String getInstalledDate(File f){
		return getInstalledDateEdition(f.lastModified(), null);
	}
	
	public String getInstalledDateEdition(long t, TimeZone timeZone){
		return android.text.format.DateFormat.getMediumDateFormat(app).format(new Date(t));
	}

	public String getInstalledDate(long t, TimeZone timeZone){
		return android.text.format.DateFormat.getMediumDateFormat(app).format(new Date(t));
	}

	public void updateDescription(LocalIndexInfo info){
		File f = new File(info.getPathToData());
		if(info.getType() == LocalIndexType.MAP_DATA){
			Map<String, String> ifns = app.getResourceManager().getIndexFileNames();
			if(ifns.containsKey(info.getFileName())) {
				try {
					Date dt = app.getResourceManager().getDateFormat().parse(ifns.get(info.getFileName()));
					info.setDescription(getInstalledDate(dt.getTime(), null));
				} catch (ParseException e) {
					e.printStackTrace();
				}
			} else {
				info.setDescription(getInstalledDate(f));
			}
		} else if(info.getType() == LocalIndexType.TILES_DATA){
			ITileSource template ;
			if(f.isDirectory() && TileSourceManager.isTileSourceMetaInfoExist(f)){
				template = TileSourceManager.createTileSourceTemplate(new File(info.getPathToData()));
			} else if(f.isFile() && f.getName().endsWith(SQLiteTileSource.EXT)){
				template = new SQLiteTileSource(app, f, TileSourceManager.getKnownSourceTemplates());
			} else {
				return;
			}
			String descr = "";
			descr += app.getString(R.string.local_index_tile_data_name, template.getName());
			if(template.getExpirationTimeMinutes() >= 0) {
				descr += "\n" + app.getString(R.string.local_index_tile_data_expire, template.getExpirationTimeMinutes());
			}
			info.setDescription(descr);
		} else if(info.getType() == LocalIndexType.SRTM_DATA){
			info.setDescription(app.getString(R.string.download_srtm_maps));
		} else if(info.getType() == LocalIndexType.WIKI_DATA){
			info.setDescription(getInstalledDate(f));
		} else if(info.getType() == LocalIndexType.TTS_VOICE_DATA){
			info.setDescription(getInstalledDate(f));
		} else if(info.getType() == LocalIndexType.DEACTIVATED){
			info.setDescription(getInstalledDate(f));
		} else if(info.getType() == LocalIndexType.VOICE_DATA){
			info.setDescription(getInstalledDate(f));
		}
	}


	public List<LocalIndexInfo> getLocalIndexData(AbstractLoadLocalIndexTask loadTask){
		Map<String, String> loadedMaps = app.getResourceManager().getIndexFileNames();
		List<LocalIndexInfo> result = new ArrayList<>();
		
		loadObfData(app.getAppPath(IndexConstants.MAPS_PATH), result, false, loadTask, loadedMaps);
		loadObfData(app.getAppPath(IndexConstants.ROADS_INDEX_DIR), result, false, loadTask, loadedMaps);
		loadTilesData(app.getAppPath(IndexConstants.TILES_INDEX_DIR), result, false, loadTask);
		loadSrtmData(app.getAppPath(IndexConstants.SRTM_INDEX_DIR), result, loadTask);
		loadWikiData(app.getAppPath(IndexConstants.WIKI_INDEX_DIR), result, loadTask);
		//loadVoiceData(app.getAppPath(IndexConstants.TTSVOICE_INDEX_EXT_ZIP), result, true, loadTask);
		loadVoiceData(app.getAppPath(IndexConstants.VOICE_INDEX_DIR), result, false, loadTask);
		loadObfData(app.getAppPath(IndexConstants.BACKUP_INDEX_DIR), result, true, loadTask, loadedMaps);
		
		return result;
	}

	public List<LocalIndexInfo> getLocalFullMaps(AbstractLoadLocalIndexTask loadTask) {
		Map<String, String> loadedMaps = app.getResourceManager().getIndexFileNames();
		List<LocalIndexInfo> result = new ArrayList<>();
		loadObfData(app.getAppPath(IndexConstants.MAPS_PATH), result, false, loadTask, loadedMaps);

		return result;
	}

	private void loadVoiceData(File voiceDir, List<LocalIndexInfo> result, boolean backup, AbstractLoadLocalIndexTask loadTask) {
		if (voiceDir.canRead()) {
			//First list TTS files, they are preferred
			for (File voiceF : listFilesSorted(voiceDir)) {
				if (voiceF.isDirectory() && !MediaCommandPlayerImpl.isMyData(voiceF) && (Build.VERSION.SDK_INT >= 4)) {
					LocalIndexInfo info = null;
					if (TTSCommandPlayerImpl.isMyData(voiceF)) {
						info = new LocalIndexInfo(LocalIndexType.TTS_VOICE_DATA, voiceF, backup, app);
					}
					if(info != null) {
						updateDescription(info);
						result.add(info);
						loadTask.loadFile(info);
					}
				}
			}

			//Now list recorded voices
			for (File voiceF : listFilesSorted(voiceDir)) {
				if (voiceF.isDirectory() && MediaCommandPlayerImpl.isMyData(voiceF)) {
					LocalIndexInfo info = null;
					info = new LocalIndexInfo(LocalIndexType.VOICE_DATA, voiceF, backup, app);
					if(info != null){
						updateDescription(info);
						result.add(info);
						loadTask.loadFile(info);
					}
				}
			}
		}
	}
	
	private void loadTilesData(File tilesPath, List<LocalIndexInfo> result, boolean backup, AbstractLoadLocalIndexTask loadTask) {
		if (tilesPath.canRead()) {
			for (File tileFile : listFilesSorted(tilesPath)) {
				if (tileFile.isFile() && tileFile.getName().endsWith(SQLiteTileSource.EXT)) {
					LocalIndexInfo info = new LocalIndexInfo(LocalIndexType.TILES_DATA, tileFile, backup, app);
					updateDescription(info);
					result.add(info);
					loadTask.loadFile(info);
				} else if (tileFile.isDirectory()) {
					LocalIndexInfo info = new LocalIndexInfo(LocalIndexType.TILES_DATA, tileFile, backup, app);

					if(!TileSourceManager.isTileSourceMetaInfoExist(tileFile)){
						info.setCorrupted(true);
					}
					updateDescription(info);
					result.add(info);
					loadTask.loadFile(info);
				}
			}
		}
	}
	
	private File[] listFilesSorted(File dir){
		File[] listFiles = dir.listFiles();
		if(listFiles == null) {
			return new File[0];
		}
		Arrays.sort(listFiles);
		return listFiles;
	}

	
	private void loadSrtmData(File mapPath, List<LocalIndexInfo> result, AbstractLoadLocalIndexTask loadTask) {
		if (mapPath.canRead()) {
			for (File mapFile : listFilesSorted(mapPath)) {
				if (mapFile.isFile() && mapFile.getName().endsWith(IndexConstants.BINARY_MAP_INDEX_EXT)) {
					LocalIndexInfo info = new LocalIndexInfo(LocalIndexType.SRTM_DATA, mapFile, false, app);
					updateDescription(info);
					result.add(info);
					loadTask.loadFile(info);
				}
			}
		}
	}
	
	private void loadWikiData(File mapPath, List<LocalIndexInfo> result, AbstractLoadLocalIndexTask loadTask) {
		if (mapPath.canRead()) {
			for (File mapFile : listFilesSorted(mapPath)) {
				if (mapFile.isFile() && mapFile.getName().endsWith(IndexConstants.BINARY_MAP_INDEX_EXT)) {
					LocalIndexInfo info = new LocalIndexInfo(LocalIndexType.WIKI_DATA, mapFile, false, app);
					updateDescription(info);
					result.add(info);
					loadTask.loadFile(info);
				}
			}
		}
	}
	
	private void loadObfData(File mapPath, List<LocalIndexInfo> result, boolean backup, AbstractLoadLocalIndexTask loadTask, Map<String, String> loadedMaps) {
		if (mapPath.canRead()) {
			for (File mapFile : listFilesSorted(mapPath)) {
				if (mapFile.isFile() && mapFile.getName().endsWith(IndexConstants.BINARY_MAP_INDEX_EXT)) {
					LocalIndexType lt = LocalIndexType.MAP_DATA;
					if(mapFile.getName().endsWith(IndexConstants.BINARY_SRTM_MAP_INDEX_EXT)) {
						lt = LocalIndexType.SRTM_DATA;
					} else if(mapFile.getName().endsWith(IndexConstants.BINARY_WIKI_MAP_INDEX_EXT)) {
						lt = LocalIndexType.WIKI_DATA;
					}
					LocalIndexInfo info = new LocalIndexInfo(lt, mapFile, backup, app);
					if(loadedMaps.containsKey(mapFile.getName()) && !backup){
						info.setLoaded(true);
					}
					updateDescription(info);
					result.add(info);
					loadTask.loadFile(info);
				}
			}
		}
	}

	public enum LocalIndexType {
		MAP_DATA(R.string.local_indexes_cat_map),
		TILES_DATA(R.string.local_indexes_cat_tile),
		SRTM_DATA(R.string.local_indexes_cat_srtm, R.drawable.ic_plugin_srtm),
		WIKI_DATA(R.string.local_indexes_cat_wiki, R.drawable.ic_plugin_wikipedia),
		TTS_VOICE_DATA(R.string.local_indexes_cat_tts, R.drawable.ic_action_volume_up),
		VOICE_DATA(R.string.local_indexes_cat_voice, R.drawable.ic_action_volume_up),
		DEACTIVATED(R.string.local_indexes_cat_backup, R.drawable.ic_type_archive);
//		AV_DATA(R.string.local_indexes_cat_av);;

		@StringRes
		private final int resId;
		@DrawableRes
		private int iconResource;

		LocalIndexType(@StringRes int resId, @DrawableRes int iconResource){
			this.resId = resId;
			this.iconResource = iconResource;
		}

		LocalIndexType(@StringRes int resId){
			this.resId = resId;
			this.iconResource = R.drawable.ic_map;

			//TODO: Adjust icon of backed up files to original type
			//if (getString(resId) == R.string.local_indexes_cat_backup) {
			//	if (i.getOriginalType() == LocalIndexType.MAP_DATA) {
			//		this.iconResource = R.drawable.ic_map;
			//	} else if (i.getOriginalType() == LocalIndexType.TILES_DATA) {
			//		this.iconResource = R.drawable.ic_map;
			//	} else if (i.getOriginalType() == LocalIndexType.SRTM_DATA) {
			//		this.iconResource = R.drawable.ic_plugin_srtm;
			//	} else if (i.getOriginalType() == LocalIndexType.WIKI_DATA) {
			//		this.iconResource = R.drawable.ic_plugin_wikipedia;
			//	} else if (i.getOriginalType() == LocalIndexType.TTS_VOICE_DATA) {
			//		this.iconResource =  R.drawable.ic_action_volume_up;
			//	} else if (i.getOriginalType() == LocalIndexType.VOICE_DATA) {
			//		this.iconResource = R.drawable.ic_action_volume_up;
			//	} else if (i.getOriginalType() == LocalIndexType.AV_DATA) {
			//		this.iconResource = R.drawable.ic_action_volume_up;
			//	}
		}
		public String getHumanString(Context ctx){
			return ctx.getString(resId);
		}
		public int getIconResource() {
			return iconResource;
		}
		public String getBasename(LocalIndexInfo localIndexInfo) {
			String fileName = localIndexInfo.getFileName();
			if (fileName.endsWith(IndexConstants.EXTRA_ZIP_EXT)) {
				return fileName.substring(0, fileName.length() - IndexConstants.EXTRA_ZIP_EXT.length());
			}
			if (fileName.endsWith(IndexConstants.SQLITE_EXT)) {
				return fileName.substring(0, fileName.length() - IndexConstants.SQLITE_EXT.length());
			}
			if (this == VOICE_DATA) {
				int l = fileName.lastIndexOf('_');
				if (l == -1) {
					l = fileName.length();
				}
				return fileName.substring(0, l);
			}
			int ls = fileName.lastIndexOf('_');
			if (ls >= 0) {
				return fileName.substring(0, ls);
			} else if(fileName.indexOf('.') > 0){
				return fileName.substring(0, fileName.indexOf('.'));
			}
			return fileName;
		}
	}

}
