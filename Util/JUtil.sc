JUtil { // Handy utility methods
	classvar catchKeyWin;
	classvar jfps;
	classvar <errorChecking = 3;
	classvar meter, scope, freqScope, treePlot;

	*showAll {
		Window.allWindows.do {|win| win.front };
	}

	*pwd {
		^thisProcess.nowExecutingPath.dirname;
	}

	*catchKeys {|callback|
		callback.isNil && { callback = {|view,char,mods,unicode,keycode| [char,keycode].postln}};

		catchKeyWin.isNil && { catchKeyWin = Window.new("I catch keystrokes", Rect(128,64,400,50)); };

		catchKeyWin.view.keyDownAction = callback;
		catchKeyWin.front;
	}

	*fpsbang {
		if(jfps.isNil) {
			jfps = JFPS.new();
		};
		^jfps.bang();
	}

	*fps {
		if(jfps.isNil) {
			jfps = JFPS.new();
		};
		^jfps.fps();
	}

	*debug {|printstring, errorlevel=3|
		if(errorlevel <= errorChecking) {
			var result = case
			{errorlevel == 1} { "!!!! ERROR: "; }
			{errorlevel == 2} { "!! WARNING: "; }
			{errorlevel == 3} { "! DEBUG: "; };

			result = result + printstring;
			Post << result << $\n;
		};
	}

	*monitor {arg serv, keepontop=false, plottree=false;
		meter = serv.meter;
		meter.window.alwaysOnTop_(keepontop).setTopLeftBounds(Rect(1320, 0, 100, 200));
		freqScope = serv.freqscope;
		freqScope.window.alwaysOnTop_(keepontop).setTopLeftBounds(Rect(900, 520, 100, 200));
		scope = serv.scope(2, 0);
		scope.window.alwaysOnTop_(keepontop).setTopLeftBounds(Rect(900, 0, 400, 500));
		if(plottree) {
			treePlot = Window.new("Node Tree", scroll: true).front.alwaysOnTop_(false);
			serv.plotTreeView(0.5, treePlot);
			treePlot.setTopLeftBounds(Rect(600, 0, 300, 850));
		};
	}


	// Open a file chooser dialog. The callback function will receive the file path selected.
	*chooseFile {|callback|
		var filepath = nil;
		FileDialog.new({ |paths|
			filepath = paths[0];
			postln("Selected path:" + filepath);
			callback.value(filepath);
		}, {
			postln("Dialog was cancelled. Try again.");
			callback.value(nil);
		});
	}

	// Read a buffer and convert to mono if desired, using a specific mix mode.
	// callback will receive the loaded Buffer object when it's ready.
	// stereo to mono mix modes are: \right \left \mix
	*loadBuf {|filepath, server, callback, mono=true, mixmode=\mix|
		Buffer.read(server, filepath, action: {|buf|
			postln("Reading Buf: " + buf);
			if( (mono == true).and { buf.numChannels == 2 } ) {
				var monobuf = Buffer.alloc(server, buf.numFrames, 1);
				buf.normalize.loadToFloatArray(action: {|arr|
					var monomix;
					monomix = Array.newClear(arr.size / 2).collect {|it, idx|
						switch(mixmode,
							\right, { arr[idx*2] },
							\left, { arr[idx*2 + 1] },
							\mix, { arr[idx*2] + arr[idx*2 + 1] },
							{ Error("Invalid mixdown option % when loading sample %".format(monomix, this)).throw; }
						);
					};
					"Array collected and buf is %".format(monobuf).postln;
					monobuf.loadCollection(monomix.normalize(-1,1), 0, { "Loaded mono buf".postln });
					"Success!".postln;
				});
				callback.value(monobuf);
			} {
				callback.value(buf);
			};
		});
	}

	// Open a file chooser dialog to choose an audio file, which will be loaded into a buffer.
	// The callback will receive the Buffer object once loaded.
	*chooseBuf {|callback, mono=true, mixmode=\mix|
		FileDialog.new({ |paths|
			var filepath = paths[0];
			postln("Selected path:" + filepath);
			JUtil.loadBuf.value(filepath, callback, mono, mixmode);
		}, {
			postln("Dialog was cancelled. Try again.");
			callback.value(nil);
		});
	}

}

// A frames per second counter
JFPS {
	var avgperiod, lasthit;

	*new {
		^super.new.init;
	}

	init {
		avgperiod = 1.0;
		lasthit = 0.0;
	}

	// Call this each time a frame happens.
	// Returns the current FPS value
	bang {
		var thishit, period;
		thishit = Process.elapsedTime();
		period = thishit - lasthit;
		lasthit = thishit;
		avgperiod = (avgperiod + period) / 2.0;
		^avgperiod;
	}

	fps {
		^avgperiod;
	}
}


// Add timestamped entries to a log file.
JLog {
	var <>fpath;
	classvar logger;

	*new {|filepath, filename|
		^super.new.init(filepath, filename);
	}

	*getLogger {|filepath, filename|
		if(this.logger.isNil) {
			this.logger = JLog.new(filepath, filename);
		};
		^this.logger;
	}

	*log {|str=""|
		this.getLogger.log(str);
	}

	init {|filepath=nil, filename=nil|
		if(filename.isNil) {
			filename = "log_sc_program" ++ ".txt";
		} {
			filename = filename ++ ".txt";
		};

		if(filepath.isNil) {
			// Log to same directory as running process
			fpath = JUtil.pwd +/+ filename;
		} {
			fpath = filepath +/+ filename;
		};
	}

	log {|str|
		var timestamp, result, fh;
		timestamp = Date.getDate.asString;
		result = timestamp + " ### " + str + "\n";
		fh = File.open(fpath, "a");
		fh.write(result);
		fh.close();
		result.postln;
		// ALT: similar to python's with X open Y
		//f = File.use(~d_path, "a", {|thefile| thefile.write("Write somethin else "+rrand(200,300)+"\n");});

	}
}


/*
Assert class that keeps a global track of the number of assert statements passed.
Can be used in one of two ways:

Assert("Some text"); // prints "ASSERT N: Some text"
...where N is the running tally of assertions...

or

ASSERT( z == 2, "Bad z value!" ); // A boolean condition, where if the assertion fails an error is thrown.

ASSERT(reset: true); // will reset the ASSERTION counter.

*/
ASSERT {

	classvar singleton;
	classvar <>num=0;

	*new {|val="", text="", reset=false|
		if(singleton.isNil) {
			singleton = super.new;
			^singleton.assert(val: val, text: text, reset: reset);
		} {
			^singleton.assert(val: val, text: text, reset: reset);
		}
	}

	assert {|val="", text="", reset=false|
		if(reset == true) {
			num = 0;
		};
		if(val.isNil) {
			val = "";
		};

		if(val.isBoolean) {
			if(val.not) {
				AssertionError("ASSERTION FAILED: %".format(text)).throw;
			}
		} {
			text = "ASSERT %: % %".format(num, val, text);
			text.postln;
			num = num + 1;
		};
		^this;
	}
}

AssertionError : Exception {
	errorString {
		^what;
	}
}

/*
A mechanism to load and cache buffers, freeing the oldest ones
when new buffers are loaded in situations where buffers are loaded
continuously and older ones need to be pruned to avoid hitting the
limit on allocatable buffers.
*/
BufferLoaderQueue {
	var <bufsQueue; // queue of loaded buffers
	var <bufsById; // dictionary of buffers by id, helps keep track of which bufs are still available
	var <loadingTasks; // polling tasks that wait until buffers are loaded
	var <maxBuffers; // maximum number of buffers before old ones start getting freed
	var <loadingPollRate = 0.2;
	var <activeServer;
	var <>currentBufIdPrefix; // bufIdPrefix, gets regenerated every 30000 buffers
	var <>currentBufId = 0; // next buffer id value, note: these ids are local to BufferLoaderQueue and not the same as SC's internal buf ids

	*new {|server, size|
		^super.new.init(server, size);
	}

	init {|server, size|
		bufsQueue = List.new;
		bufsById = TwoWayIdentityDictionary.new;
		loadingTasks = List.new;
		maxBuffers = size;

		currentBufIdPrefix = Array.fill( 3, { rrand(97, 122).asAscii } ).join.asSymbol;

		if(server.isNil) {
			server = Server.default;
		};
		activeServer = server;
	}

	// Get a buffer by id, returns nil if no buffer exists
	bufAt {|id|
		^bufsById.at(id);
	}

	/*
	Load a list of buffers
	wavs  a list of pseudoobjects/events with a 'file' field containing filepath
	      they will get populated with status and duration fields, and a temporary buf field that will get removed in pr_cache_buf
	allowDuplicates  if false, only loads bufs for wavs that do not already have a valid 'bufManagerId'
	                 pointing to a buffer loaded into memory. if true, creates a duplicate buffer in the queue
	                 even if one already exists... usually you want this to be false.
	done_callback  function called when buffers are loaded, receives a list of buffers that were loaded

	NOTE!: old bufs in the queues might be freed, it's your responsibily to make sure
	       that if these bufs are being used elsewhere in client code they are also freed!
	*/
	load_bufs {|wavs, allowDuplicates=false, doneAction|
		var loadlist = List.new;
		if(wavs.isKindOf(SequenceableCollection).not) {
			wavs = [wavs];
		};
		wavs.do {|wav, idx|
			var isDuplicate = false, allowLoad = true;
			if(wav.bufManagerId.notNil) {
				if(bufsById.at(wav.bufManagerId).notNil) {
					isDuplicate = true;
					if(allowDuplicates.not) { allowLoad = false };
				};
			};

			if(allowLoad) {
				wav.status = \loading;
				loadlist.add(wav);
				Buffer.read(activeServer, wav.file, action: {|bf|
					var loadwav = wav;
					loadwav.buf=bf; // TODO: remember to free this later!
					loadwav.status=\ready;
					loadwav.duration = bf.duration;
				});
			};
		};

		// Run a thread that finishes when all buffers are loaded
		// and then runs the callback function
		loadingTasks.add({
			var not_loaded = true;
			var loading = loadlist;
			while { loading.any {|wv| wv.status == \loading } } {
				loadingPollRate.wait;
			};

			// we use a circular buffer to cache buffers and free them
			// after a while so that we don't just create infinite buffers
			loading.do {|wav, idx|
				this.pr_cache_buf(wav);
			};

			// Finally, run callback and pass list of loaded buffers
			doneAction.value(loading);
		}.fork(AppClock));

	}


	// private function, adds a new buffer to the queue
	//   expects a pseudoobject with a 'buf' field containing the loaded buffer
	// it will get populated with bufManagerId, bufManager,  populated
	// removes and frees the oldest buffer if maxBuffers is reached
	pr_cache_buf {|pobj|
		var oldbuf, oldbufid, newbuf;

		// If we're out of space, pop the oldest buf from queue & dictionary
		if(bufsQueue.size >= maxBuffers) {
			oldbuf = bufsQueue.pop;
			oldbufid = bufsById.getID(oldbuf);
			bufsById.removeAt(oldbufid);
		};

		// Generate a new id & add the new buf
		pobj.bufManagerId = (currentBufIdPrefix.asString ++ currentBufId).asSymbol;
		currentBufId = currentBufId + 1;
		if(currentBufId > 10000) {
			currentBufIdPrefix = Array.fill( 3, { rrand(97, 122).asAscii } ).join.asSymbol;
			currentBufId = 0;
		};
		pobj.bufManager = this; // add a reference to the buf manager, the buf should only be referenced here from now on!!
		newbuf = pobj.buf;
		pobj.removeAt(\buf); // remove the buf key, the BufManager should be used instead to access the buffer
		bufsQueue.addFirst(newbuf); // push on to the queue
		bufsById.put(pobj.bufManagerId, newbuf); // put into the dictionary
		if(oldbuf.notNil) { oldbuf.free }; // finally, free the old buf from the server & return it
		^oldbuf;
	}


	// Stop all loading tasks and free all buffers.
	clear {
		loadingTasks.do {|rtn| rtn.stop };
		bufsQueue.do {|buf| buf.free };
	}
}



// System for using symbols as language proxies for more complex objects.
// Certain classes, called SymbolProxyManagers, can also keep internal dictionaries
// of symbol>object and link them into the SymbolProxy system. This is useful, for example,
// when building livecoding microlanguages that use symbols as placeholders for objects.
// See Classes: Beat, FX
SymbolProxyManager {
	classvar <registeredSymbols; // maybe not possible?

	*initClass {
		registeredSymbols = Dictionary.new;
	}

	// Register a symbol as a proxy for an object manager or for a specific object
	//   In the case of a manager, it must respond to the interface for SymbolProxyManager
	*registerSymbolProxy {|symbol, target|
		if(symbol.class != Symbol) { "'%' must be of type Symbol".format(symbol).throw };
		if(target.isNil) { "Target cannot be nil".throw };
		registeredSymbols.put(symbol, target);
		^target;
	}

	*unregisterSymbolProxy {|symbol|
		^registeredSymbols.removeAt(symbol);
	}


	*performProxyMethod { |symbol, selector, args|
		var targetObject = registeredSymbols.at(symbol);

		if(targetObject.notNil) {

			if( ( targetObject.isKindOf(Class).and {targetObject.superclasses.includes(SymbolProxyManager)} || ( targetObject.isKindOf(SymbolProxyManager) )).and { targetObject.getManager.notNil } ) {
				targetObject = targetObject.getManager.at(symbol); // fetch from the manager
			};


			if(targetObject.class.findRespondingMethodFor(selector).isNil) {
				targetObject=nil;// no corresponding method > target is nil
			};

		};

		if(targetObject.notNil) {
			^targetObject.performList(selector, args);
		} {
			^nil;
		};

	}

}





/* ---------------------------------------------------
ERROR TYPES


---------------------------------------------------  */

// Errors in custom microlanguages
LangError : Error {
	errorString {
		^what;
	}
}







/* ------------------------------------------------
//////////////////////////////////////////
// ADDITIONS TO CORE CLASSES

------------------------------------------------ */


/* -------------------------------
GENERAL
Server.
Color additions.
Etc.
----------------------- */
+ Object {
	isBoolean {
		^[true.class, false.class, True.class, False.class].includes(this.class);
	}
}

+ Server {
	*quickBoot {
		var tmp = Server.internal;
		tmp.options.memSize = 32768;
		Server.default = tmp.boot;
		^tmp;
	}
}

+ Color {
	*orange {|val=1.0, alpha=1.0|
		^Color.new(val, val, 0, alpha);
	}
}

+ Char {
	isInteger {
		^this.ascii.inclusivelyBetween(48, 57);
	}
}

+ String {
	// specific runInTerminal using gnome-terminal
	runInMintTerminal {
		"gnome-terminal -- bash -c \"%; exec bash\"".format(this.shellQuote).unixCmd;
	}

	// Test if a string is a valid float
	isFloat {
		^"^[\-]?[0-9]+[\.][0-9]*$".matchRegexp(this);
	}

	// Test if a string is a valid int
	isInteger {
		^"^[\-]?[0-9]+$".matchRegexp(this);
	}
}


/*------------------------------------
DATE TIME

Date.getDate.rawSeconds / 86400; // days
Date.getDate.rawSeconds
Date.getDate.daysSinceEpoch
Date.getDate.calcFirstDayOfYear
d = "181230_150017";
e = "190105_000000";
Date.getDate.daysDiff(Date.fromStamp(e))
*/
+ Date {

	/*
	Creates a date object from a datetime stamp of the format YYMMDD_HHMMSS
	*/
	*fromStamp {|datetimestamp|
		^super.new.initFromStamp(datetimestamp);
	}

	initFromStamp {|dts|
		if(dts.isNil.or { dts.isKindOf(String).not.or { dts.size != 13 } }) {
			throw("Bad Time Stamp Format %".format(dts));
		};
		year = dts[..1].asInteger;
		year = if(year<70) { year+2000 } { year+1900 };
		month = dts[2..3].asInteger;
		day = dts[4..5].asInteger;
		hour = dts[7..8].asInteger;
		minute = dts[9..10].asInteger;
		second = dts[11..12].asInteger;
		// dayOfWeek = ; // TODO
		this.calcSecondsSinceEpoch;
	}

	/*
	Returns number of days since civil 1970-01-01.  Negative values indicate
	days prior to 1970-01-01.
	Preconditions:  y-m-d represents a date in the civil (Gregorian) calendar
	m is in [1, 12]
	d is in [1, last_day_of_month(y, m)]
	y is "approximately" in [numeric_limits<Int>::min()/366, numeric_limits<Int>::max()/366]
	Exact range of validity is:
	[civil_from_days(numeric_limits<Int>::min()),
	civil_from_days(numeric_limits<Int>::max()-719468)]
	*/
	daysSinceEpoch {
		var era, yoe, doy, doe, res;
		var y=year,m=month,d=day;
		y = y - (m <= 2).asInteger;
		era = if(y>=0) {y} {y-399}; era = era / 400;
		yoe = y - (era * 400);      // [0, 399]
		doy = (153*(m + (m > 2).if({-3},{9})) + 2)/5 + d-1;  // [0, 365]
		doe = yoe * 365 + yoe/4 - yoe/100 + doy;         // [0, 146096]
		res = era * 146097 + doe.asInteger - 719468;
		^res;
	}

	calcSecondsSinceEpoch {
		var res;
		res = this.daysSinceEpoch + second + minute*60 + hour*3600;
		this.rawSeconds_(res);
		^res;
	}

	/*
	This will give the day of the week as 0 = Sunday, 6 = Saturday. This result can easily be adjusted by adding a constant before or after the modulo 7
	*/
	calcFirstDayOfYear {
		var res;
		res = (year*365 + trunc((year-1) / 4) - trunc((year-1) / 100) + trunc((year-1) / 400)) % 7;
		^res;
	}

	/*
	this-that in days
	*/
	daysDiff {|that|
		^(this.daysSinceEpoch - that.daysSinceEpoch)
	}
}

/* -------------------------------------
MUSIC NOTATION
and RAPID PLAYBACK
-------------------------------- */

+ Synth {
	gate {|val=0|
		^this.set(\gate, val);
	}
}

+ String {

	isNoteSymbol {
		^"^[a-gA-G][s#\+bf\-]?[0-9]?$".matchRegexp(this);
	}

	rootOctave {
		var mid, res = nil;
		if(this.isNoteSymbol) {
			res = [0, 5];
			mid = this.tomidi;
			res[1] = (mid / 12).asInteger; // octave
			res[0] = mid - (12 * res[1]); // root
		};
		^res;
	}


	notecps {
		if([\rest, \rr, \r].includes(this.asSymbol)) {
			^Rest();
		};
		^this.tomidi.midicps;
	}

	f { ^this.notecps; } // to frequency

	m { ^this.tomidi; } // to midi

	// convert note symbol as string into midi note
	tomidi {
		var twelves, ones, octaveIndex, midis, octave=false;
		midis = Dictionary[($c->0),($d->2),($e->4),($f->5),($g->7),($a->9),($b->11)];
		ones = midis.at(this[0].toLower);
		if( this.size > 1 ) {
			if(this[1].isDecDigit) {
				octave=true;
				octaveIndex = 1;
			} {
				if( (this[1] == $#) || (this[1].toLower == $s) || (this[1] == $+) ) {
					ones = ones + 1;
				} {
					if( (this[1] == $b) || (this[1].toLower == $f) || (this[1] == $-) ) {
						ones = ones - 1;
					};
				};
				if( this.size > 2 ) {
					octaveIndex = 2;
					octave=true;
				};
			};
		};
		if(octave) {
			twelves = (this.copyRange(octaveIndex, this.size).asInteger) * 12;
		} {
			twelves = 5*12; // default without octave indicator
		};
		^(twelves + ones);
	}

	// Convert a string of note values
	// to an array of note symbols
	// "ab2 bb2 c3" becomes [\ab2, \bb2, \c3]
	// chords can be represented by slashes
	// "ab2/bb2/c3 d4 eb4/c5" becomes [[\ab2, \bb2, \c3], \d4, [\eb4, \c5]]
	notes {
		var res;
		var register = 5;
		// remove any newlines or excess whitespace
		res = this.stripWhiteSpace.replace($\n, " ").replace("   ", " ").replace("  ", " ");
		res = res.split($ );
		res = res.collect {|notestr|
			var notesymbol, notereg;
			if(notestr.includes($/)) { // chord
				notesymbol = notestr.split($/).collect {|noteval| noteval.notes };
			} {
				if(["r", "rest", "rr"].includesEqual(notestr)) {
					notesymbol = \rr
				} {
					if([$&, $b, $f].includes(notestr[1])) { notestr[1] = $b };
					if([$#, $s].includes(notestr[1])) { notestr[1] = $s };
					if(notestr.wrapAt(-1).isInteger) {
						register = notestr.wrapAt(-1).asString.asInteger;
					} {
						notestr = notestr ++ register;
					};
					notesymbol = notestr.asSymbol;
				};
			};
			notesymbol;
		};
		if(res.size == 1) { res = res[0] };
		^res;
	}

	// Convert a string of numbers - fractional or decimal - into an array of durations
	// "1/2 0.23 1" becomes [0.5, 0.23, 1]
	durs {
		var res;
		// remove any newlines or excess whitespace
		res = this.stripWhiteSpace.replace($\n, " ").replace("   ", " ").replace("  ", " ");
		res = res.split($ );
		res = res.collect {|durstr|
			var val;
			if(durstr.includes($/)) {
				val = durstr.interpret;
			} {
				val = durstr.asFloat;
			};
			val;
		};
		^res;
	}
	// convert a string of notes into frequencies
	freqs { ^this.notes.f }

	// play a scale of intervals (half step/whole step)
	// "--.---.".play; // major diatonic scale
	playScale {|root=\c5, tuning=\et12, amp=0.2, dur=0.5, delta=0.6, pan=0.0|
		var etsemitone = 2**(1/12), etwhole = 2**(1/6);
		if(root.isNumber.not) { root = root.f };
		{
			this.do {|char|
				root.play(amp, pan, dur);
				root.postln;
				if(char == $-) { // whole step
					root = root * etwhole;
				};
				if(char == $.) { // half step
					root = root * etsemitone;
				};
				delta.wait;
			};
			root.play(amp, pan, dur);
		}.fork;
		^this;
	}

	// postln with formatting
	//postf {|...args| ^this.format(*args).postln; }
}





/***
Additions to existing classes...

Allow direct manipulation of sequence name symbols....
***/


+ Symbol {


	// Used by the SymbolProxy system to call the appropriate messages when
	// a symbol is used as an object proxy..
	doesNotUnderstand { | selector...args |
		var res = SymbolProxyManager.performProxyMethod(this, selector, args);

		if(res.isNil) {
			"% does not understand method %".format(this.class, selector).throw;
		};

		^res;
	}

	checkIsNoteSymbol {
		if(this.asString.isNoteSymbol.not) {
			"'%' is not a note symbol".format(this).throw;
		};
	}

	notecps { ^this.asString.notecps; }

	// To frequency
	f { ^this.notecps; }

	// To midi
	m { ^this.asString.m; }

	// Plays note symbols as notes
	play {|amp=0.2, pan=0.0, dur=0.5|
		^this.notecps.play(amp, pan,dur);
	}

	// Transpositions & Smart Manipulations
	tp{|semitones|
		var res = this;
		if([\r, \rr, \rest].includes(this).not) {
			res = (this.m + semitones).midinote;
		};
		^res;
	}
	transpose{|semitones|
		^this.tp(semitones)
	}

	/*
	++{|semitones|
	var res = this;
	if(semitones.isInteger) {
	res = this.tp(semitones);
	}
	^res;
	}
	--{|semitones|
	var res = this;
	if(semitones.isInteger) {
	res = this.tp(semitones * -1);
	}
	^res;
	}
	*/


	// diverge/converge away/towards a specific key
	// with a "strength" factor
	diverge {|key, scale, strength=0.5|
		var res = this;
		key = key ? \g;
		scale = scale ? Scale.major;
		^res;
	}
	converge {|key, scale, strength=0.5|
		var res = this;
		key = key ? \g;
		scale = scale ? Scale.major;
		^res;
	}

	// convert note symbol to root and octave in a 12TET scale
	// c5 is root=0 octave=5
	rootOctave {
		this.checkIsNoteSymbol;
		^this.asString.rootOctave;
	}

	// convert note symbol to midi note
	notemidi {
		^this.asString.notemidi;
	}
}

+ SimpleNumber {
	play {|amp=0.2, pan=0.0, dur=0.5| // plays number as frequency
		var syn = {
			var numharms=10, falloff=0.6, sig;
			var freqs = Array.newClear(numharms);
			var amps = Array.newClear(numharms);
			numharms.do {|idx|
				freqs[idx] = this * (idx+1);
				amps[idx] = falloff**idx;
			};
			sig = SinOsc.ar(freqs, mul: amps).sum;
			Pan2.ar(sig, pan, amp) * EnvGen.ar(Env.perc, timeScale: dur, doneAction: 2)
		}.play;
		^syn;
	}

	// Overwrites from midinote.sc in SC3plugins
	midinote {
		var midi, notes;
		midi = (this + 0.5).asInteger;
		notes = ["c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"];
		^(notes[midi%12] ++ midi.div(12)).asSymbol
	}

	// Converts from frequency to note symbol
	note {
		^this.cpsmidi.midinote;
	}

}




/* ------------
Collections
-----------*/

+ SequenceableCollection {
	// to frequency & midi note
	f { ^this.collect(_.f); }

	m { ^this.collect(_.m); }

	note { ^this.collect(_.note); }

	// treat the array as a Smpl playback spec and play it using Smpl.splay()
	splay {|rate=1.0, amp=1.0, out=0, co=20000, rq=1.0, pan=0, loops=1, autogate=1|
		Smpl.splaySpec(this, rate, amp, out, co, rq, pan, loops, autogate);
	}

	// treat the array as a note sequence and play it using the default synth
	play {|amp, pan, dur=0.5, delta=0.6|
		{
			this.do {|note|
				note.play(amp, pan, dur);
				delta.wait;
			}
		}.fork;
		^this;
	}

	midinote { ^this.performUnaryOp('midinote') }
	notemidi { ^this.performUnaryOp('notemidi') }

	// TRANSPOSIIONS
	tp {|semitones| ^this.collect(_.tp(semitones)); }

	transpose {|semitones| ^this.tp(semitones); }

	/*
	When arrays are sent from other languages (e.g. Python) as OSC arguments,
	often the array is encoded with actual string literals "[" "]" marking the bounds
	of values.

	For example, the osc message with 7 arguments...
	/to_sc sim t 162.12 Xarray [22.17, 21.77, -66.43] Yarray [0.271, 0.383]

	Will be received in SC as the array...
	[ /to_sc, "sim", "t", 162.12, "Xarray", "[", 22.17, 21.77, -66.43, "]", "Yarray", "[", "0.271, 0.383, "]" ]

	This method searches the target array for [ and ] string/char literals beginning at start_idx
	if an array is found this method returns an array containing two elements:
	0: the array that was found  1: the index directly following the end of the found array in the receiver
	element 0 will be an empty array if no array was found

	Note, this allows repeatedly searching an OSC message for sequential embedded arrays
	#arr1, idx = msg.parseOscArray();
	#arr2, idx = msg.parseOscArray(idx);
	*/
	parseOscArray {|start_idx=0|
		var res = List.new();
		var idx = start_idx;
		var arrayFound = false;
		var complete = false;
		while ({ (arrayFound == false).and({ idx < this.size }) }, {
			//"Read: %   Type: %".format(this[idx], this[idx].class).postln;
			if(this[idx].asString == "[") {
				arrayFound = true;
			};
			idx = idx+1;
		});

		while ( { (complete == false).and( { idx < this.size } ) }, {
			//"Read: %   Type: %".format(this[idx], this[idx].class).postln;
			if(this[idx].asString == "]") {
				complete = true;
			} {
				res.add(this[idx]);
			};
			idx = idx+1;
		});

		if(complete == true) {
			res = res.asArray;
		} {
			res = [];
		};

		^[res, idx];
	}

}



