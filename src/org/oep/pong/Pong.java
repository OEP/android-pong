package org.oep.pong;

import org.oep.pong.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public class Pong extends Activity {
	private PongView mPongView;
	private AlertDialog mAboutBox;
	private RefreshHandler mRefresher;
	protected PowerManager.WakeLock mWakeLock;
	
	class RefreshHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Pong.this.hideAboutBox();
		}
		
		public void sleep(long delay) {
			this.removeMessages(0);
			this.sendMessageDelayed(obtainMessage(0), delay);
		}
	}
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        
        setContentView(R.layout.pong_view);
        mPongView = (PongView) findViewById(R.id.pong);
        mPongView.update();
        mRefresher = new RefreshHandler();
        
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        final PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Pong");
        mWakeLock.acquire();
    }
    
    protected void onStop() {
    	super.onStop();
		mPongView.stop();
    }
    
    protected void onResume() {
    	super.onResume();
    	mPongView.resume();
    }
    
    protected void onDestroy() {
    	super.onDestroy();
    	mPongView.releaseResources();
    	mWakeLock.release();
    }
   
    
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
		boolean result = super.onCreateOptionsMenu(menu);
		
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.game_menu, menu);
		
		return result;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
    	int id = item.getItemId();
    	boolean flag = false;
    	
    	switch(id) {
    	case R.id.menu_0p: flag = true; mPongView.setPlayerControl(false, false); break;
    	case R.id.menu_1p: flag = true; mPongView.setPlayerControl(false, true); break;
    	case R.id.menu_2p: flag = true; mPongView.setPlayerControl(true, true); break;
    	case R.id.menu_about:
    		mAboutBox = new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_info)
    			.setTitle(R.string.about).setMessage(R.string.about_msg).show();
    		mPongView.pause();
    		mRefresher.sleep(5000);
    		break;
    	case R.id.quit: this.finish(); return true;
    	
    	case R.id.menu_toggle_sound:
    		mPongView.toggleMuted();
    		break;
    	}
    	
    	if(flag) {
	    	mPongView.setShowTitle(false);
	    	mPongView.newGame();
    	}
    	
    	
    	return true;
    }
    
    public void hideAboutBox() {
    	if(mAboutBox != null) {
    		mAboutBox.hide();
    		mAboutBox = null;
    	}
    }
    
    public static final String DB_PREFS = "Pong";
    public static final String PREF_MUTED = "pref_muted";
}