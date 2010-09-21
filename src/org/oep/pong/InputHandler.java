package org.oep.pong;

import android.os.Build;
import android.view.MotionEvent;

public abstract class InputHandler {
	
	public static InputHandler getInstance() {
		if(Integer.parseInt(Build.VERSION.SDK) < 5) {
			return SingleInput.Holder.sInstance;
		}
		else {
			return MultiInput.Holder.sInstance;
		}
	}
	
	public abstract int getTouchCount(MotionEvent e);
	public abstract float getX(MotionEvent e, int i);
	public abstract float getY(MotionEvent e, int i);
	
	private static class MultiInput extends InputHandler {
		private static class Holder {
			private static final MultiInput sInstance = new MultiInput();
		}

		@Override
		public int getTouchCount(MotionEvent e) {
			return e.getPointerCount();
		}

		@Override
		public float getX(MotionEvent e, int i) {
			return e.getX(i);
		}

		@Override
		public float getY(MotionEvent e, int i) {
			return e.getY(i);
		}
	}
	
	private static class SingleInput extends InputHandler {
		private static class Holder {
			private static final SingleInput sInstance = new SingleInput();
		}

		@Override
		public int getTouchCount(MotionEvent e) {
			return 1;
		}

		@Override
		public float getX(MotionEvent e, int i) {
			return e.getX();
		}

		@Override
		public float getY(MotionEvent e, int i) {
			return e.getY();
		}
	}
}
