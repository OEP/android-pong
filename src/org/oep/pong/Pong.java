package org.oep.pong;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;

public class Pong extends Activity {

	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_title);
		setListeners();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.game_menu, menu);
		return true;
	}
	
	
	protected void setListeners () {
		this.findViewById(R.id.title_btnNoPlayer)
		.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startGame(false,false);
			}
		});
		
		this.findViewById(R.id.title_btnOnePlayer)
		.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startGame(false,true);
			}
		});
		
		this.findViewById(R.id.title_btnTwoPlayer)
		.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startGame(true,true);
			}
		});
	}
	
	protected void startGame(boolean redPlayer, boolean bluePlayer) {
		Intent i = new Intent(this, GameActivity.class);
		i.putExtra(GameActivity.EXTRA_BLUE_PLAYER, bluePlayer);
		i.putExtra(GameActivity.EXTRA_RED_PLAYER, redPlayer);
		startActivity(i);
	}
	
}