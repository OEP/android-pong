package org.oep.pong;

import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.widget.Toast;

/**
 * This class is the main viewing window for the Pong game. All the game's
 * logic takes place within this class as well.
 * @author OEP
 *
 */
public class PongView extends View implements OnTouchListener, OnKeyListener {
	/** Debug tag */
	@SuppressWarnings("unused")
	private static final String TAG = "PongView";
	protected static final int FPS = 30;
	
	public static final int
		STARTING_LIVES = 1,
		PLAYER_PADDLE_SPEED = 10;
	
	/**
	 * This is mostly deprecated but kept around if the need
	 * to add more game states comes around.
	 */
	private State mCurrentState = State.Running;
	private State mLastState = State.Stopped;
	public static enum State { Running, Stopped}

	/** Flag that marks this view as initialized */
	private boolean mInitialized = false;
	
	/** Preferences loaded at startup */
	private int mBallSpeedModifier;
	
	/** Lives modifier */
	private int mLivesModifier;
		
	/** AI Strategy */
	private int mAiStrategy;
	
	/** CPU handicap */
	private int mCpuHandicap;
	
	/** Starts a new round when set to true */
	private boolean mNewRound = true;
	
	/** Keeps the game thread alive */
	private boolean mContinue = true;
	
	/** Mutes sounds when true */
	private boolean mMuted = false;

	private Paddle mRed, mBlue;
	
	/** Touch boxes for various functions. These are assigned in initialize() */
	private Rect mPauseTouchBox;

	/** Timestamp of the last frame created */
	private long mLastFrame = 0;

	protected Ball mBall = new Ball();

	/** Random number generator */
	private static final Random RNG = new Random();
	
	/** Pool for our sound effects */
	protected SoundPool mPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
	
	protected int mWinSFX, mMissSFX, mPaddleSFX, mWallSFX;
	
	/** Paint object */
	private final Paint mPaint = new Paint();

	/** Padding for touch zones and paddles */
	private static final int PADDING = 3;
	
	/** Scrollwheel sensitivity */
	private static final int SCROLL_SENSITIVITY = 80;

	/** Redraws the screen according to FPS */
	private RefreshHandler mRedrawHandler = new RefreshHandler();
	
	/** Flags indicating who is a player */
	private boolean mRedPlayer = false, mBluePlayer = false;

	/**
	 * An overloaded class that repaints this view in a separate thread.
	 * Calling PongView.update() should initiate the thread.
	 * @author OEP
	 *
	 */
	class RefreshHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			PongView.this.update();
			PongView.this.invalidate(); // Mark the view as 'dirty'
		}
		
		public void sleep(long delay) {
			this.removeMessages(0);
			this.sendMessageDelayed(obtainMessage(0), delay);
		}
	}

    /**
     * Creates a new PongView within some context
     * @param context
     * @param attrs
     */
    public PongView(Context context, AttributeSet attrs) {
        super(context, attrs);
        constructView();
    }

    public PongView(Context context, AttributeSet attrs, int defStyle) {
    	super(context, attrs, defStyle);
    	constructView();
    }
    
    /**
     * Set the paddles to their initial states and as well the ball.
     */
    private void constructView() {
    	setOnTouchListener(this);
    	setOnKeyListener(this);
    	setFocusable(true);
    	
    	Context ctx = this.getContext();
    	loadPreferences( PreferenceManager.getDefaultSharedPreferences(ctx) );
    	loadSFX();
    }
    
    protected void loadSFX() {
    	Context ctx = getContext();
    	mWinSFX = mPool.load(ctx, R.raw.wintone, 1);
    	mMissSFX = mPool.load(ctx, R.raw.ballmiss, 1);
    	mPaddleSFX = mPool.load(ctx, R.raw.paddle, 1);
    	mWallSFX = mPool.load(ctx, R.raw.wall, 1);
    }
    
    protected void loadPreferences(SharedPreferences prefs) {
    	Context ctx = getContext();
    	Resources r = ctx.getResources();
    	
    	mBallSpeedModifier = Math.max(0, prefs.getInt(Pong.PREF_BALL_SPEED, 0));
    	mMuted = prefs.getBoolean(Pong.PREF_MUTED, mMuted);
    	mLivesModifier = Math.max(0, prefs.getInt(Pong.PREF_LIVES, 2));
    	mCpuHandicap = Math.max(0, Math.min(PLAYER_PADDLE_SPEED-1, prefs.getInt(Pong.PREF_HANDICAP, 4)));
    	
    	String strategy = prefs.getString(Pong.PREF_STRATEGY, null);
    	String strategies[] = r.getStringArray(R.array.values_ai_strategies);
    	
    	mAiStrategy = 0;
    	// Linear-search the array for the appropriate strategy index =/
    	for(int i = 0; strategy != null && strategy.length() > 0 && i < strategies.length; i++) {
    		if(strategy.equals(strategies[i])) {
    			mAiStrategy = i;
    			break;
    		}
    	}
    }
    
    /**
     * The main loop. Call this to update the game state.
     */
    public void update() {
    	if(getHeight() == 0 || getWidth() == 0) {
    		mRedrawHandler.sleep(1000 / FPS);
    		return;
    	}
    	
    	if(!mInitialized) {
    		initializePongView();
    		mInitialized = true;
    	}
    	
    	long now = System.currentTimeMillis();
    	if(gameRunning() && mCurrentState != State.Stopped) {
	    	if(now - mLastFrame >= 1000 / FPS) {
	    		if(mNewRound) {
	    			nextRound();
	    			mNewRound = false;
	    		}
	    		doGameLogic();
	    	}
    	}
    	
    	// We will take this much time off of the next update() call to normalize for
    	// CPU time used updating the game state.
    	
    	if(mContinue) {
    		long diff = System.currentTimeMillis() - now;
    		mRedrawHandler.sleep(Math.max(0, (1000 / FPS) - diff) );
    	}
    }

    /**
     * All of the game's logic (per game iteration) is in this function.
     * Given some initial game state, it computes the next game state.
     */
	private void doGameLogic() {
		float px = mBall.x;
		float py = mBall.y;
		
		mBall.move();
		
		// Shake it up if it appears to not be moving vertically
		if(py == mBall.y && mBall.serving() == false) {
			mBall.randomAngle();
		}
		
		// Do some basic paddle AI
		if(!mRed.player) doAI(mRed, mBlue);
		else mRed.move();
		
		if(!mBlue.player) doAI(mBlue, mRed);
		else mBlue.move();
		
		handleBounces(px,py);
		
		// See if all is lost
		if(mBall.y >= getHeight()) {
			mNewRound = true;
			mBlue.loseLife();
			
			if(mBlue.living()) playSound(mMissSFX);
			else playSound(mWinSFX);
		}
		else if (mBall.y <= 0) {
			mNewRound = true;
			mRed.loseLife();
			if(mRed.living()) playSound(mMissSFX);
			else playSound(mWinSFX);
		}
	}
	
	protected void handleBounces(float px, float py) {
		handleTopFastBounce(mRed, px, py);
		handleBottomFastBounce(mBlue, px, py);
		
		// Handle bouncing off of a wall
		if(mBall.x <= Ball.RADIUS || mBall.x >= getWidth() - Ball.RADIUS) {
			mBall.bounceWall();
			playSound(mWallSFX);
			if(mBall.x == Ball.RADIUS)
				mBall.x++;
			else
				mBall.x--;
		}
		
	}
	
	protected void handleTopFastBounce(Paddle paddle, float px, float py) {
		if(mBall.goingUp() == false) return;
		
		float tx = mBall.x;
		float ty = mBall.y - Ball.RADIUS;
		float ptx = px;
		float pty = py - Ball.RADIUS;
		float dyp = ty - paddle.getBottom();
		float xc = tx + (tx - ptx) * dyp / (ty - pty);
		
		if(ty < paddle.getBottom() && pty > paddle.getBottom()
				&& xc > paddle.getLeft() && xc < paddle.getRight()) {
			
			mBall.x = xc;
			mBall.y = paddle.getBottom() + Ball.RADIUS;
			mBall.bouncePaddle(paddle);
			playSound(mPaddleSFX);
			increaseDifficulty();
		}
	}
	
	protected void handleBottomFastBounce(Paddle paddle, float px, float py) {
		if(mBall.goingDown() == false) return;
		
		float bx = mBall.x;
		float by = mBall.y + Ball.RADIUS;
		float pbx = px;
		float pby = py + Ball.RADIUS;
		float dyp = by - paddle.getTop();
		float xc = bx + (bx - pbx) * dyp / (pby - by);
		
		if(by > paddle.getTop() && pby < paddle.getTop()
				&& xc > paddle.getLeft() && xc < paddle.getRight()) {
			
			mBall.x = xc;
			mBall.y = paddle.getTop() - Ball.RADIUS;
			mBall.bouncePaddle(paddle);
			playSound(mPaddleSFX);
			increaseDifficulty();
		}
	}
	
	private void doAI(Paddle cpu, Paddle opponent) {
		switch(mAiStrategy) {
		case 2:	aiFollow(cpu); break;
		case 1:	aiExact(cpu); break;
		default: aiPrediction(cpu,opponent); break;
		}
	}
	
	/**
	 * A generalized Pong AI player. Takes a Rect object and a Ball, computes where the ball will
	 * be when ball.y == rect.y, and tries to move toward that x-coordinate. If the ball is moving
	 * straight it will try to clip the ball with the edge of the paddle.
	 * @param cpu
	 */
	private void aiPrediction(Paddle cpu, Paddle opponent) {
		Ball ball = new Ball(mBall);
		
		// Special case: move torward the center if the ball is blinking
		if(mBall.serving()) {
			cpu.destination = getWidth() / 2;
			cpu.move(true);
			return;
		}
		
		// Something is wrong if vy = 0.. let's wait until things fix themselves
		if(ball.vy == 0) return;
		
		// Y-Distance from ball to Rect 'cpu'
		float cpuDist = Math.abs(ball.y - cpu.centerY());
		// Y-Distance to opponent.
		float oppDist = Math.abs( ball.y - opponent.centerY() );
		
		// Distance between two paddles.
		float paddleDistance = Math.abs(cpu.centerY() - opponent.centerY());
		
		// Is the ball coming at us?
		boolean coming = (cpu.centerY() < ball.y && ball.vy < 0)
			|| (cpu.centerY() > ball.y && ball.vy > 0);
		
		// Total amount of x-distance the ball covers
		float total = ((((coming) ? cpuDist : oppDist + paddleDistance)) / Math.abs(ball.vy)) * Math.abs( ball.vx );
		
		// Playable width of the stage
		float playWidth = getWidth() - 2 * Ball.RADIUS;
		
		
		float wallDist = (ball.goingLeft()) ? ball.x - Ball.RADIUS : playWidth - ball.x + Ball.RADIUS;
		
		// Effective x-translation left over after first bounce 
		float remains = (total - wallDist) % playWidth;
		
		// Bounces the ball will incur
		int bounces = (int) ((total) / playWidth);
		
		boolean left = (bounces % 2 == 0) ? !ball.goingLeft() : ball.goingLeft();
		
		cpu.destination = getWidth() / 2;
		
		// Now we need to compute the final x. That's all that matters.
		if(bounces == 0) {
			cpu.destination = (int) (ball.x + total * Math.signum(ball.vx));
		}
		else if(left) {
			cpu.destination = (int) (Ball.RADIUS + remains);
		}
		else { // The ball is going right...
			cpu.destination = (int) ((Ball.RADIUS + playWidth) - remains);
		}
		
		// Try to give it a little kick if vx = 0
		int salt = (int) (System.currentTimeMillis() / 10000);
		Random r = new Random((long) (cpu.centerY() + ball.vx + ball.vy + salt));
		int width = cpu.getWidth();
		cpu.destination = (int) bound(
				cpu.destination + r.nextInt(2 * width - (width / 5)) - width + (width / 10),
				0, getWidth()
		);
		cpu.move(true);
	}
	
	private void aiExact(Paddle cpu) {
		cpu.destination = (int) mBall.x;
		cpu.setPosition(cpu.destination);
	}
	
	private void aiFollow(Paddle cpu) {
		cpu.destination = (int) mBall.x;
		cpu.move(true);
	}
	
	/**
	 * Knocks up the framerate a bit to keep it difficult.
	 */
	private void increaseDifficulty() {
		mBall.speed++;
	}

	/**
	 * Set the state, start a new round, start the loop if needed.
	 * @param next, the next state
	 */
	public void setMode(State next) {
    	mCurrentState = next;
    	nextRound();
    	update();
    }
	
    /**
     * Reset the paddles/touchboxes/framespersecond/ballcounter for the next round.
     */
    private void nextRound() {
    	serveBall();
    }
    
    /**
     * Initializes objects needed to carry out the game.
     * This should be called once as soon as the View has reached
     * its inflated size.
     */
    private void initializePongView() {
    	initializePause();
    	initializePaddles();
    }
    
    private void initializePause() {
    	int min = Math.min(getWidth() / 4, getHeight() / 4);
    	int xmid = getWidth() / 2;
    	int ymid = getHeight() / 2;
    	mPauseTouchBox = new Rect(xmid - min, ymid - min, xmid + min, ymid + min);
    }
    
    private void initializePaddles() {
    	Rect redTouch = new Rect(0,0,getWidth(),getHeight() / 8);
    	Rect blueTouch = new Rect(0, 7 * getHeight() / 8, getWidth(), getHeight());
    	
    	mRed = new Paddle(Color.RED, redTouch.bottom + PADDING);
    	mBlue = new Paddle(Color.BLUE, blueTouch.top - PADDING - Paddle.PADDLE_THICKNESS);
    	
    	mRed.setTouchbox( redTouch );
    	mBlue.setTouchbox( blueTouch );
    	
    	mRed.setHandicap(mCpuHandicap);
    	mBlue.setHandicap(mCpuHandicap);
    	
    	mRed.player = mRedPlayer;
    	mBlue.player = mBluePlayer;
    	
    	mRed.setLives(STARTING_LIVES + mLivesModifier);
    	mBlue.setLives(STARTING_LIVES + mLivesModifier);
    }
    
    /**
     * Reset ball to an initial state
     */
    private void serveBall() {
    	mBall.x = getWidth() / 2;
    	mBall.y = getHeight() / 2;
    	mBall.speed = Ball.SPEED + mBallSpeedModifier;
    	mBall.randomAngle();
    	mBall.pause();
    }
    
    protected float bound(float x, float low, float hi) {
    	return Math.max(low, Math.min(x, hi));
    }
    
    /**
     * Use for keeping track of a position.
     * @author pkilgo
     *
     */
    class Point {
    	private int x, y;
    	Point() {
    		x = 0; y = 0;
    	}
    	
    	Point(int x, int y) {
    		this.x = x; this.y = y;
    	}
    	
    	public int getX() { return x; }
    	public int getY() { return y ; }
    	public void set(double d, double e) { this.x = (int) d; this.y = (int) e; }
    	
    	public void translate(int i, int j) { this.x += i; this.y += j; }
    	
    	@Override
    	public String toString() {
    		return "Point: (" + x + ", " + y + ")";
    	}
    }
    
    public void onSizeChanged(int w, int h, int ow, int oh) {
    }
    
    /**
     * Paints the game!
     */
    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if(mInitialized == false) {
        	return;
        }
        
    	Context context = getContext();
    	
        // Draw the paddles / touch boundaries
    	mRed.draw(canvas);
    	mBlue.draw(canvas);

    	// Draw touchboxes if needed
    	if(gameRunning() && mRed.player && mCurrentState == State.Running)
        	mRed.drawTouchbox(canvas);
        
        if(gameRunning() && mBlue.player && mCurrentState == State.Running)
        	mBlue.drawTouchbox(canvas);
        
        // Draw ball stuff
        mPaint.setStyle(Style.FILL);
        mPaint.setColor(Color.WHITE);
        
        mBall.draw(canvas);
        
        // If either is a not a player, blink and let them know they can join in!
        // This blinks with the ball.
        if(mBall.serving()) {
        	String join = context.getString(R.string.join_in);
        	int joinw = (int) mPaint.measureText(join);
        	
        	if(!mRed.player) {
        		mPaint.setColor(Color.RED);
        		canvas.drawText(join, getWidth() / 2 - joinw / 2, mRed.touchCenterY(), mPaint);
        	}
        	
        	if(!mBlue.player) {
        		mPaint.setColor(Color.BLUE);
        		canvas.drawText(join, getWidth() / 2 - joinw / 2, mBlue.touchCenterY(), mPaint);
        	}
        }
        
        // Show where the player can touch to pause the game
        if(mBall.serving()) {
        	String pause = context.getString(R.string.pause);
        	int pausew = (int) mPaint.measureText(pause);
        
        	mPaint.setColor(Color.GREEN);
        	mPaint.setStyle(Style.STROKE);
        	canvas.drawRect(mPauseTouchBox, mPaint);
        	canvas.drawText(pause, getWidth() / 2 - pausew / 2, getHeight() / 2, mPaint);
        }

    	// Paint a PAUSED message
        if(gameRunning() && mCurrentState == State.Stopped) {
        	String s = context.getString(R.string.paused);
        	int width = (int) mPaint.measureText(s);
        	int height = (int) (mPaint.ascent() + mPaint.descent()); 
        	mPaint.setColor(Color.WHITE);
        	canvas.drawText(s, getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, mPaint);
        }
        
        // Draw a 'lives' counter
    	mPaint.setColor(Color.WHITE);
    	mPaint.setStyle(Style.FILL_AND_STROKE);
    	for(int i = 0; i < mRed.getLives(); i++) {
    		canvas.drawCircle(Ball.RADIUS + PADDING + i * (2 * Ball.RADIUS + PADDING),
    				PADDING + Ball.RADIUS,
    				Ball.RADIUS,
    				mPaint);
    	}
    	
    	for(int i = 0; i < mBlue.getLives(); i++) {
    		canvas.drawCircle(Ball.RADIUS + PADDING + i * (2 * Ball.RADIUS + PADDING),
    				getHeight() - PADDING - Ball.RADIUS,
    				Ball.RADIUS,
    				mPaint);
    	}
        
        // Announce the winner!
        if(!gameRunning()) {
        	mPaint.setColor(Color.GREEN);
        	String s = "You both lose";
        	
        	if(!mBlue.living()) {
        		s = context.getString(R.string.red_wins);
        		mPaint.setColor(Color.RED);
        	}
        	else if(!mRed.living()) {
        		s = context.getString(R.string.blue_wins);
        		mPaint.setColor(Color.BLUE);
        	}
        	
        	int width = (int) mPaint.measureText(s);
        	int height = (int) (mPaint.ascent() + mPaint.descent()); 
        	canvas.drawText(s, getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, mPaint);
        }        
    }

    /**
     * Touching is the method of movement. Touching the touchscreen, that is.
     * A player can join in simply by touching where they would in a normal
     * game.
     */
	public boolean onTouch(View v, MotionEvent mo) {
		if(v != this || !gameRunning()) return false;
		
		// We want to support multiple touch and single touch
		InputHandler handle = InputHandler.getInstance();

		// Loop through all the pointers that we detected and 
		// process them as normal touch events.
		for(int i = 0; i < handle.getTouchCount(mo); i++) {
			int tx = (int) handle.getX(mo, i);
			int ty = (int) handle.getY(mo, i);
			
			// Bottom paddle moves when we are playing in one or two player mode and the touch
			// was in the lower quartile of the screen.
			if(mBlue.player && mBlue.inTouchbox(tx,ty)) {
				mBlue.destination = tx;
			}
			else if(mRed.player && mRed.inTouchbox(tx,ty)) {
				mRed.destination = tx;
			}
			else if(mo.getAction() == MotionEvent.ACTION_DOWN && mPauseTouchBox.contains(tx, ty)) {
				if(mCurrentState != State.Stopped) {
					mLastState = mCurrentState;
					mCurrentState = State.Stopped;
				}
				else {
					mCurrentState = mLastState;
					mLastState = State.Stopped;
				}
			}
			
			// In case a player wants to join in...
			if(mo.getAction() == MotionEvent.ACTION_DOWN) {
				if(!mBlue.player && mBlue.inTouchbox(tx,ty)) {
					mBlue.player = true;
				}
				else if(!mRed.player && mRed.inTouchbox(tx,ty)) {
					mRed.player = true;
				}
			}
		}
		
		return true;
	}
	
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if(!gameRunning()) return false;
		
		if(mBlue.player == false) {
			mBlue.player = true;
			mBlue.destination = mBlue.centerX();
		}
		
		switch(event.getAction()) {
		case MotionEvent.ACTION_MOVE:
			mBlue.destination = (int) Math.max(0, Math.min(getWidth(), mBlue.destination + SCROLL_SENSITIVITY * event.getX()));
			break;
		}
		
		return true;
	}
    
	/**
	 * Reset the lives, paddles and the like for a new game.
	 */
	public void newGame() {
		resetPaddles();
		serveBall();
		resumeLastState();
	}
	
	/**
	 * Resets the lives and the position of the paddles.
	 */
	private void resetPaddles() {
		int mid = getWidth() / 2;
		mRed.setPosition(mid);
		mBlue.setPosition(mid);
		mRed.destination = mid;
		mBlue.destination = mid;
		mRed.setLives(STARTING_LIVES);
		mBlue.setLives(STARTING_LIVES);
	}
	
	/**
	 * This is kind of useless as well.
	 */
	private void resumeLastState() {
		if(mLastState == State.Stopped && mCurrentState == State.Stopped) {
			mCurrentState = State.Running;
		}
		else if(mCurrentState != State.Stopped) {
			// Do nothing
		}
		else if(mLastState != State.Stopped) {
			mCurrentState = mLastState;
			mLastState = State.Stopped;
		}
	}
	
	public boolean gameRunning() {
		return mInitialized && mRed != null && mBlue != null
			&& mRed.living() && mBlue.living();
	}
	
	public void pause() {
		mLastState = mCurrentState;
		mCurrentState = State.Stopped;
	}
	
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		return false;
	}

	public void setPlayerControl(boolean red, boolean blue) {
		mRedPlayer = red;
		mBluePlayer = blue;
	}

	public void resume() {
		mContinue = true;
		update();
	}
	
	public void stop() {
		mContinue = false;
	}
	
	/**
	 * Release all resource locks.
	 */
	public void release() {
		mPool.release();
	}
	
	public void toggleMuted() {
		this.setMuted(!mMuted);
	}
	
	public void setMuted(boolean b) {
		// Set the in-memory flag
		mMuted = b;
		
		// Grab a preference editor
		Context ctx = this.getContext();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
		SharedPreferences.Editor editor = settings.edit();
		
		// Save the value
		editor.putBoolean(Pong.PREF_MUTED, b);
		editor.commit();
		
		// Output a toast to the user
		int rid = (mMuted) ? R.string.sound_disabled : R.string.sound_enabled;
		Toast.makeText(ctx, rid, Toast.LENGTH_SHORT).show();
	}
	
	private void playSound(int rid) {
		if(mMuted == true) return;
		mPool.play(rid, 0.2f, 0.2f, 1, 0, 1.0f);
	}
	
	class Ball {
		public float x, y, xp, yp, vx, vy;
		public float speed = SPEED;
		
		protected double mAngle;
		protected boolean mNextPointKnown = false;
		protected int mCounter = 0;
		
		public Ball() {
			findVector();
		}
		
		public Ball(Ball other) {
			x = other.x;
			y = other.y;
			xp = other.xp;
			yp = other.yp;
			vx = other.vx;
			vy = other.vy;
			speed = other.speed;
			mAngle = other.mAngle;
		}
		
		protected void findVector() {
			vx = (float) (speed * Math.cos(mAngle));
			vy = (float) (speed * Math.sin(mAngle));
		}
		
		public boolean goingUp() {
			return mAngle >= Math.PI;
		}
		
		public boolean goingDown() {
			return !goingUp();
		}
		
		public boolean goingLeft() {
			return mAngle <= 3 * Math.PI / 2 && mAngle > Math.PI / 2;
		}
		
		public boolean goingRight() {
			return !goingLeft();
		}
		
		public double getAngle() {
			return mAngle;
		}
		
		public boolean serving() {
			return mCounter > 0;
		}
		
		public void pause() {
			mCounter = 60;
		}
		
		public void move() {
			if(mCounter <= 0) {
				x = keepX(x + vx); 
				y += vy;
			}
			else {
				mCounter--;
			}
		}
		
		public void randomAngle() {
			setAngle( Math.PI / 2 + RNG.nextInt(2) * Math.PI + Math.PI / 2 * RNG.nextGaussian() );
		}
		
		public void setAngle(double angle) {
			mAngle = angle % (2 * Math.PI);
			mAngle = boundAngle(mAngle);
			findVector();
		}
		
		public void draw(Canvas canvas) {
	        if((mCounter / 10) % 2 == 1 || mCounter == 0)
	        	canvas.drawCircle(x, y, Ball.RADIUS, mPaint);
		}
		
		/**
		 * Tells us if the ball collides with a rectangle.
		 * @param r, the rectangle
		 * @return true if the ball is colliding, false if not
		 */
		public boolean collides(Paddle p) {
			return p.collides(this); 
		}
		
		/**
		 * Method bounces the ball across a vertical axis. Seriously it's that easy.
		 * Math failed me when figuring this out so I guessed instead.
		 */
		public void bouncePaddle(Paddle p) {
			double angle;
			
			// up-right case
			if(mAngle >= Math.PI) {
				angle = 4 * Math.PI - mAngle;
			}
			// down-left case
			else {
				angle = 2 * Math.PI - mAngle;
			}
			
			angle %= (2 * Math.PI);
			angle = salt(angle, p);
//			normalize(p);
			setAngle(angle);
		}

		/**
		 * Bounce the ball off a horizontal axis.
		 */
		public void bounceWall() {
			setAngle(3 * Math.PI - mAngle);
		}
		
		protected double salt(double angle, Paddle paddle) {
			int cx = paddle.centerX();
			double halfWidth = paddle.getWidth() / 2;
			double change = 0.0;
			
			if(goingUp()) change = SALT * ((cx - x) / halfWidth);
			else change = SALT * ((x - cx) / halfWidth);
			
			return boundAngle(angle, change);
		}
		
		/**
		 * Normalizes a ball's position after it has hit a paddle.
		 * @param r The paddle the ball has hit.
		 */
		protected void normalize(Paddle p) {
			// Quit if the ball is outside the width of the paddle
			if(x < p.getLeft() || x > p.getRight()) {
				return;
			}
			
			// Case if ball is above the paddle
			if(y < p.getTop()) {
				y = Math.min(y, p.getTop() - Ball.RADIUS);
			}
			else if(y > p.getBottom()) {
				y = Math.max(y, p.getBottom() + Ball.RADIUS);
			}
		}
		
		/**
		 * Bounds sum of <code>angle</code> and <code>angleChange</code> to the side of the
		 * unit circle that <code>angle</code> is on.
		 * @param angle The initial angle.
		 * @param angleChange Amount to add to angle.
		 * @return bounded angle sum
		 */
		protected double boundAngle(double angle, double angleChange) {
			return boundAngle(angle + angleChange, angle >= Math.PI);
		}
		
		protected double boundAngle(double angle) {
			return boundAngle(angle, angle >= Math.PI);
		}
		
		/**
		 * Bounds an angle in radians to a subset of the top
		 * or bottom part of the unit circle.
		 * @param angle The angle in radians to bound.
		 * @param top Flag which indicates if we should bound to the top or not.
		 * @return the bounded angle
		 */
		protected double boundAngle(double angle, boolean top) {
			if(top) {
				return Math.max(Math.PI + BOUND, Math.min(2 * Math.PI - BOUND, angle));
			}

			return Math.max(BOUND, Math.min(Math.PI - BOUND, angle));
		}
		

		/**
		 * Given it a coordinate, it transforms it into a proper x-coordinate for the ball.
		 * @param x, the x-coord to transform
		 * @return
		 */
		protected float keepX(float x) {
			return bound(x, Ball.RADIUS, getWidth() - Ball.RADIUS);
		}
		
		public static final double BOUND = Math.PI / 9;
		public static final float SPEED = 4.0f; 
		public static final int RADIUS = 4;
		public static final double SALT = 4 * Math.PI / 9;
	}

	class Paddle {
		protected int mColor;
		protected Rect mRect;
		protected Rect mTouch;
		protected int mHandicap = 0;
		protected int mSpeed = PLAYER_PADDLE_SPEED;
		protected int mLives = STARTING_LIVES;
		
		public boolean player = false;

		public int destination;
		
		public Paddle(int c, int y) {
			mColor = c;
			
			int mid = PongView.this.getWidth() / 2;
			mRect = new Rect(mid - PADDLE_WIDTH, y,
					mid + PADDLE_WIDTH, y + PADDLE_THICKNESS);
			destination = mid;
		}
		
		public void move() {
			move(mSpeed);
		}
		
		public void move(boolean handicapped) {
			move((handicapped) ? mSpeed - mHandicap : mSpeed);
		}
		
		public void move(int s) {
			int dx = (int) Math.abs(mRect.centerX() - destination);
			
			if(destination < mRect.centerX()) {
				mRect.offset( (dx > s) ? -s : -dx, 0);
			}
			else if(destination > mRect.centerX()) {
				mRect.offset( (dx > s) ? s : dx, 0);
			}
		}
		
		public void setLives(int lives) {
			mLives = Math.max(0, lives);
		}
		
		public void setPosition(int x) {
			mRect.offset(x - mRect.centerX(), 0);
		}
		
		public void setTouchbox(Rect r) {
			mTouch = r;
		}
		
		public void setSpeed(int s) {
			mSpeed = (s > 0) ? s : mSpeed;
		}
		
		public void setHandicap(int h) {
			mHandicap = (h >= 0 && h < mSpeed) ? h : mHandicap; 
		}
		
		public boolean inTouchbox(int x, int y) {
			return mTouch.contains(x, y);
		}
		
		public void loseLife() {
			mLives = Math.max(0, mLives - 1);
		}
		
		public boolean living() {
			return mLives > 0;
		}
		
		public int getWidth() {
			return Paddle.PADDLE_WIDTH;
		}
		
		public int getTop() {
			return mRect.top;
		}
		
		public int getBottom() {
			return mRect.bottom;
		}
		
		public int centerX() {
			return mRect.centerX();
		}
		
		public int centerY() {
			return mRect.centerY();
		}
		
		public int getLeft() {
			return mRect.left;
		}
		
		public int getRight() {
			return mRect.right;
		}
		
		public int touchCenterY() {
			return mTouch.centerY();
		}
		
		public int getLives() {
			return mLives;
		}
		
		public void draw(Canvas canvas) {
			mPaint.setColor(mColor);
			mPaint.setStyle(Style.FILL);
			canvas.drawRect(mRect, mPaint);
		}
		
		public void drawTouchbox(Canvas canvas) {
			mPaint.setColor(mColor);
			mPaint.setStyle(Style.STROKE);
			
			// Heuristic for deciding which line to paint:
			// draw the one closest to middle
			int mid = getHeight() / 2;
			int top = Math.abs(mTouch.top - mid), bot = Math.abs(mTouch.bottom - mid);
			float y = (top < bot) ? mTouch.top : mTouch.bottom;
			canvas.drawLine(mTouch.left, y, mTouch.right, y, mPaint);
		}
		
		public boolean collides(Ball b) {
			return b.x >= mRect.left && b.x <= mRect.right && 
			b.y >= mRect.top - Ball.RADIUS && b.y <= mRect.bottom + Ball.RADIUS;
		}
		
		/** Thickness of the paddle */
		private static final int PADDLE_THICKNESS = 10;
		
		/** Width of the paddle */
		private static final int PADDLE_WIDTH = 40;
	}
}
