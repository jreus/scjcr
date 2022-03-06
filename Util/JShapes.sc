/*
Envelopes, Scales, Gestures, Waveshaping Functions and other Shaping Utilities
*/

+ Env {

	*halfSine {arg start=0, end=1, curve=4;
		var mid = (start-end) / 2;
		mid = end + mid;
		^Env([start,mid,end],[0.5,0.5],[curve,-1 * curve]);
	}
}


+ Scale {
	// Put custom scales here that should be loaded at startup
	*loadCustom {
		this.all.put(\depth, Scale([0, 0.01, 0.02, 11.2, 36, 45], 6, Tuning([400,5000,6000,200,300,560]/400), "Depth"));
	}

	/*
	@returns an array of ratios of given length from a specific start point and step size.

	@usage
	r = Scale.major.ratios2(0,10,3);
	Pbind(*[freq:Pseq(120*r),dur:0.1,amp:0.1]).play;
	r = Scale.iraq.ratios2(-10,12,1);
	Pbind(*[freq:Pseq(1220*r),dur:0.1,amp:0.1]).play;
	r = Scale.partch_u6.ratios2(-3,10,1);
	Pbind(*[freq:Pseq(220*r),dur:0.1,amp:0.1]).play;

	*/
	ratios2 {|start=0, len=7, step=1|
		var startoct, endoct, sc, end, octrange, degrees, zeroidx;
		var stpos,endpos;
		degrees = [];
		sc = this;
		startoct = ceil(start.abs / sc.degrees.size) * start.sign;
		end = start+(len*step);
		endoct = ceil(end.abs / sc.degrees.size) * end.sign;
		octrange = (startoct..endoct);
		zeroidx = octrange.indexIn(0) * sc.degrees.size;
		(startoct..endoct).do{|i| degrees = degrees ++ (sc.degrees + (sc.tuning.size * i)) };
		stpos = (zeroidx + start).asInteger;
		endpos = (stpos + (len*step)).asInteger - 1;
		degrees = degrees.copySeries(stpos, stpos+1, endpos).select({|it,i| i % step == 0; });
		^degrees.midiratio;
	}
}


// Waveshaping functions (add to MathLib?)
+ UGen {

	step {
		^(this > 0);
	}

	sigmoid {
		^(1 / (1 + exp(-1 * this)))
	}

	// Rectified Linear Unit Function
	relu {
		^ max(this, 0);
	}
}




