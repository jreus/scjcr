/*
Beatmaker
(c) 2021 Jonathan Reus

*/

/*
@usage

*/

Beat : SymbolProxyManager {
	var <clock;
	var <server;
	var <tracksGroup; // should come before the fx/master group but after recordings
	var <playersByName;
	var <isPlaying;

	classvar <singleton;
	classvar <>verbose = true;

	*new {|beatclock, serv, synthgroup|
		if(singleton.isNil) {
			^super.new.init(beatclock, serv, synthgroup);
		} {
			^singleton;
		}
	}

	*getManager {
		^singleton;
	}

	getManager {
		^this;
	}

	init {|beatclock, serv, synthgroup|
		clock = beatclock;
		server = serv;
		tracksGroup = synthgroup;
		this.prLoadSynthDefsEventTypes();
		isPlaying = false;
		playersByName = Dictionary.new();
		singleton = this;
	}


	// Load any necessary SynthDefs and Event Types used by Beat
	prLoadSynthDefsEventTypes {
		var insertSynthName = \trackInsert_2ch;
		if(SynthDescLib.global.synthDescs.at(insertSynthName).isNil) {
			SynthDef(insertSynthName, {|bus, amp=1.0, dest=0|
				Out.ar(dest, In.ar(bus, 2) * amp);
			}).add;
		};
	}

	gui {}


	// Sequencer accessor by symbol name
	at {|name|
		^playersByName.at(name);
	}

	// Mute sequence with given name to mute value (true/1 = muted)
	// name can also be a collection of names, in which case, the
	// muteval is applied to all sequences in the collection
	mute {|names, muteval=true|
		var seq;
		if(muteval.class === Integer) {
			muteval = (muteval > 0);
		};

		if(names.class.isKindOf(Collection)) {
			names.do {|name|
				seq = playersByName.at(name);
				if(seq.notNil) { seq.mute(muteval) };
			};
		} {
			seq = playersByName.at(names);
			if(seq.notNil) { seq.mute(muteval) };
		};
	}

	muteAll {|muteval=true|
		playersByName.do(_.mute(muteval));
	}

	// Sort of like the inverse of mute
	//  unmutes the named sequence(s) and mutes all other sequences
	solo {|names|
		this.muteAll;
		this.mute(names, false);
	}



	// Destroy a sequencer if it exists
	// Destroy all sequences if name is nil
	xx {|name=nil|
		if(name.notNil) {
			var seq = playersByName.at(name);
			if(seq.notNil) {
				this.unregister(seq);
				// Destroy seq
				seq.xx;
			} {
				"Beat.clear: Sequence '%' does not exist".format(name).error;
			}
		} {
			var allseqs;

			// name is nil, delete all
			"Clear all sequences".warn;

			allseqs = List.new;
			playersByName.keysValuesDo {|nm, seq|
				allseqs.add([nm, seq]);
			};

			allseqs.do {|sq|
				this.unregister(sq[1]);
				sq[1].xx;
			};

		}
	}

	// Unregister an EventSequence from being managed by this Beat player
	unregister {|eventSeq|
		if(playersByName.at(eventSeq.id).isNil) {
			"Beat.unregister: Attempted to unregister '%' but this sequence name is not registered".format(eventSeq.id).error;
		} {
			playersByName.removeAt(eventSeq.id);
			SymbolProxyManager.unregisterSymbolProxy(eventSeq.id);
		}

	}

	// Register an EventSequence to be managed by this Beat player
	register {|eventSeq|
		if(playersByName.at(eventSeq.id).isNil) {
			playersByName.put(eventSeq.id, eventSeq);
			SymbolProxyManager.registerSymbolProxy(eventSeq.id, Beat);
		} {
			"Beat.register: Attempted to unregister '%' but this sequence name is already registered ... ignoring".format(eventSeq.id).error;
		}
	}


	//********** Create / reference a LIVE SEQUENCER ************//
	// in : the input bus to record from
	// must be followed with a call to .rec or .recStart
	live {|name, pattern, zeroDegree, sdurscale, outbus, start, dur, rate, pan, autoplay=true, stepd=nil, in=0, timeout=30|
		var seq, smpl, sid;
		name = name.asSymbol;
		sid = "ls_" ++ name;

		// 1. get live sample, or create one if it doesn't already exist...
		smpl = Smpl.at(sid);
		if(smpl.isNil) {
			smpl = Smpl.prepareLiveSample(sid);
			if(Beat.verbose) {
				"Allocating new LiveSample buffer '%': %".format(sid, smpl).warn;
			};
		};

		// 2. create or update the sequence
		seq = this.sam(name, sid, pattern, zeroDegree, sdurscale, outbus, start, dur, rate, pan, stepd);
		seq.liveSampler = true; // protection flag against using catch and catchStart accidentally

		^seq;
	}

	//********** Create / reference a SAMPLE SEQUENCER ************//
	sam {|name, smpl=nil, autoplay=true|
		var seq;
		seq = playersByName.at(name);
		if(seq.isNil) {
			if(name.class != Symbol) {
				"Bad sequence name '%'! Sequence name must be a Symbol".format(name).throw;
			};

			// Make a new Sample Sequence & register it
			seq = SampleSequence.new(server, this, tracksGroup, name);
			this.register(seq);

			if(smpl.notNil) {
				seq.smpl(smpl);
			};

			if(isPlaying && autoplay) {
				"Start Playback with clock %".format(clock).warn;
				seq.play(clock);
			};
		} {
			if(smpl.notNil) {
				seq.smpl(smpl);
			};
		};
		^seq;
	}


	// Old sampleseq accessor with lots of arguments
	setSam {|name, sampleName, pattern, zeroDegree, sdurscale, out,
		start, dur, rate, pan, autoplay=true, stepd|
		var seq;
		seq = playersByName.at(name);
		if(seq.isNil) {
			if(name.class != Symbol) {
				"Bad sequence name '%'! Sequence name must be a Symbol".format(name).throw;
			};

			// Make a new Sample Sequence & register it
			seq = SampleSequence.new(server, this, tracksGroup, name, sampleName, pattern, zeroDegree, sdurscale, out, start, dur, rate, pan, stepd);
			this.register(seq);

			if(isPlaying && autoplay) {
				"Start Playback with clock %".format(clock).warn;
				seq.play(clock);
			};
		} {
			// update existing seq
			seq.pr_update(sampleName, pattern, zeroDegree, sdurscale, out, start, dur, rate, pan, stepd);
		};

		^seq;
	}



	//********** Create / reference a SYNTH SEQUENCER ************//
	syn {|name, instrumentName, pattern, rootPitch, durScale, outbus, pan, scale, autoplay=true|
		var seq;
		instrumentName = instrumentName.asSymbol;
		seq = playersByName.at(name);
		if(seq.isNil) {
				if(name.class != Symbol) {
					"Bad sequence name '%'! Sequence name must be a Symbol".format(name).throw;
				};

			seq = SynthSequence.new(server, this, tracksGroup, name, instrumentName, pattern, rootPitch, durScale, outbus, pan, scale);

			this.register(seq);

			if(isPlaying && autoplay) {
				"Start playback of Synth Sequence with clock %".format(clock).warn;
				seq.play(clock);
			};
		} {
			seq.pr_update(instrumentName, pattern, rootPitch, durScale, outbus, pan, scale);
		};

		^seq;
	}



	play {
		playersByName.keysValuesDo({|id, player|
			player.play(clock);
		});
		isPlaying = true;
	}

	// Stop all patterns
	stop {
		playersByName.keysValuesDo({|id, player|
			player.stop();
		});
		isPlaying = false;
	}

}






/***
Superclass EventSequence
***/
EventSequence {
	classvar <ampMap;
	classvar <scaleDegreeMap;

	var <id;
	var <parentBeat; // parent Beat object

	// Track Insert
	var <trackBus; // sequence always sends output to this bus
	var <trackFX;  // insert synth receiving sequencer signals..
	var <trackAmp=1.0;
	var <muted=false;
	var <outBus;

	// Pattern / Pdef
	var <pdefId;
	var <pbindef;
	var <patternString;
	var <pitchPatternString;
	var <durationScale=1.0;
	var <eventPanning=0.0;
	var <zeroDegree=0;
	var <zeroOctave=5;
	var <pitchScale;
	var <fixedStepDelta;

	*initClass {
		ampMap = ($0: 0, $1: 0.1, $2: 0.15, $3: 0.2, $4: 0.25, $5: 0.3, $6: 0.35, $7: 0.4, $8: 0.45, $9: 0.5, $a: 0.55, $b: 0.6, $c: 0.7, $d: 0.8, $e: 0.9, $f: 1.0, Rest(): Rest());
		scaleDegreeMap = ($0: 0, $1: 1, $2: 2, $3: 3, $4: 4, $5: 5, $6: 6, $7: 7, $8: 8, $9: 9, $a: 10, $b: 11, $c: 12, $d: 13, $e: 14, $f: 15, Rest(): Rest());
		//this.pr_loadEventTypes;
	}

	*new {|serv, parent, synthgroup, seqid, outbus|
		^super.new.prInitEventSequence(serv, parent, synthgroup, seqid, outbus);
	}

	prInitEventSequence {|serv, parent, synthgroup, seqid, outbus|
		id = seqid;
		parentBeat = parent;
		pdefId = id ++ "_player" ++ rand(100);
		trackBus = Bus.audio(serv, 2);
		trackFX = Synth(\trackInsert_2ch, [\bus, trackBus, \amp, trackAmp, \dest, outbus], synthgroup, \addToTail);
	}




	//************ PUBLIC API ***************//

	// stop any playback and free up resources for this sequencer...
	xx {
		this.stop;
		if(pbindef.notNil) {
			pbindef.clear;
		};

		trackFX.free;
		trackBus.free;

		if(parentBeat.notNil) {
			parentBeat.unregister(this);
		}
	}





	//****************************************
	// Track Insert Controls... these only modify the track insert synth
	//   they don't touch the pdef
	//****************************************
	amp {|ampVal|
		trackAmp = ampVal;
		if(muted.not) {
			trackFX.set(\amp, trackAmp);
		};
		^this;
	}

	mute {|muteVal=true|
		muted = muteVal;
		if(muted) {
			trackFX.set(\amp, 0);
		} {
			trackFX.set(\amp, trackAmp);
		};
		^this;
	}

	// Set output bus for this sequencer's track insert...
	out {|outbus|
		if(outbus.notNil) {

			if(outbus.isKindOf(Number).not.and { outbus.isKindOf(Bus).not }) {
				{
					outbus = outbus.bus;
				}.try({
					"Invalid output bus '%'".format(outbus).throw;
				});
			};

			outBus = outbus;
			trackFX.set(\dest, outBus);

			if(Beat.verbose) {
				"Set % track destination to '%'".format(id, outBus).warn;
			};

		}
		^this;
	}





	//*********************************
	// Pattern / Pbind Controls...
	//   NOTE: Many of these do not rebuild the pattern
	//         which you will have to do manually
	//*********************************

	pan {|pan|
		if(pan.notNil) {
			eventPanning = pan;
		}
		^this;
	}


	// duration scale : a percentage (float)
	dur {|dur|
		if(dur.notNil) {
			durationScale = dur;
		}
	}


	stepd {|stepdval, rebuild=false|
		fixedStepDelta = stepdval;
		if(rebuild) {
			this.pr_setPattern();
		};
		^this;
	}


	// MELODY PARAMETERS
	// see the event pitch model: https://depts.washington.edu/dxscdoc/Help/Tutorials/Streams-Patterns-Events5.html


	// musical scale : a scale (Scale)
	scale {|ascale|
		if(ascale.notNil) {
			if(ascale.isKindOf(Scale)) {
				pitchScale = ascale;
				if(pbindef.notNil) {
					Pbindef(pdefId, \scale, pitchScale);
				};
			} {
				"'%' is not a Scale".format(ascale).throw;
			};
		};
		^this;
	}

	// pitch of 0 degree in scale-based notation :
	//             a note symbol \c5 or a scale degree
	// combines root and octave in the event pitch model
	zeroPitch {|newzeropitch|
		if(newzeropitch.notNil) {
			if(newzeropitch.isKindOf(Symbol)) {
				// note symbol
				var rt, oct;
				#rt, oct = newzeropitch.rootOctave;
				zeroDegree = rt;
				zeroOctave = oct;
			} {
				if(newzeropitch.isKindOf(Number)) {
					// degree set independently of octave
					// could also belong to a non 12TET scale
					zeroDegree = newzeropitch.asInteger;
				} {
					"Invalid zero pitch '%' must be a note symbol or integer degree".format(newzeropitch).throw;
				};
			};

			if(pbindef.notNil) {
				Pbindef(pdefId, \root, zeroDegree, \octave, zeroOctave);
			};
		};
		^this;
	}

	// convenience alias for zeroPitch
	zero {|newzeropitch|
		^this.zeroPitch(newzeropitch);
	}


	// chromatic transposition
	ctrans {|semitones=0|
		if(pbindef.notNil) {
			Pbindef(pdefId, \ctranspose, semitones);
		} {
			"Cannot set ctrans on % - build Pdef first!".format(this.id).throw;
		};
	}

	// modal transposition
	mtrans {|degrees=0|
		if(pbindef.notNil) {
			Pbindef(pdefId, \mtranspose, degrees);
		} {
			"Cannot set mtrans on % - build Pdef first!".format(this.id).throw;
		};
	}

	// gamut transposition (???)
	gtrans {|gpitch=0|
		if(pbindef.notNil) {
			Pbindef(pdefId, \gtranspose, gpitch);
		} {
			"Cannot set gtrans on % - build Pdef first!".format(this.id).throw;
		};
	}



	// Start and stop the Pdef
	play {|clock, quant|
		if(clock.isNil) {
			clock = TempoClock;
		};

		if(quant.isNil) {
			quant = Quant(clock.beatsPerBar, 0, 0);
		};

		if(pbindef.notNil) {
			pbindef.play(quant: [clock.beatsPerBar, 0, 0], argClock: clock, protoEvent: ());
		} {
			"No pattern compiled, ignoring play command".warn;
		};
		^this;
	}

	stop {
		pbindef.stop();
		^this;
	}

}



/*
 ____                        _      ____
/ ___|  __ _ _ __ ___  _ __ | | ___/ ___|  ___  __ _
\___ \ / _` | '_ ` _ \| '_ \| |/ _ \___ \ / _ \/ _` |
 ___) | (_| | | | | | | |_) | |  __/___) |  __/ (_| |
|____/ \__,_|_| |_| |_| .__/|_|\___|____/ \___|\__, |
                      |_|                         |_|
*/
SampleSequence : EventSequence {

	// Meta...
	var <>liveSampler=false; // enables some functionality, like catch methods

	// SampleFile...
	var <sampleName;
	var <sampleFileFrames;
	var <sampleFileDuration;
	var <kitID;

	// Pattern playback...
	var <sampleStartFrame=0;
	var <sampleEndFrame=nil;
	var <samplePlaybackDuration;
	var <samplePlayRate=1.0;
	var <samplePanning=0.0;

	// Pattern state
	var <ampPattern;

	// start: in seconds / dur : in seconds
	*new {|serv, parent, synthgroup, seqid, smplname, pattern=nil, zeropitch, sampledurscale, outbus, start, dur, rate, pan, stepd|
		^super.new(serv, parent, synthgroup, seqid, outbus).init(smplname, pattern, zeropitch, sampledurscale, outbus, start, dur, rate, pan, stepd);
	}

	// start: in seconds / dur : in seconds
	init {|smplname, pattern, zeropitch, sampledurscale, outbus, start, dur, rate, pan, stepd|

		this.pr_update(smplname, pattern, zeropitch, sampledurscale, outbus, start, dur, rate, pan, stepd);

	}

	// Update the currently running sequence
	pr_update {|smplname, pattern, zeropitch, sdurscale, outbus, start_s, dur_s, rate, pan, stepd|

		this.smpl(smplname);
		this.out(outbus);
		this.zeroPitch(zeropitch);
		this.stepd(stepd);
		this.pan(pan);

		this.dur(sdurscale);
		this.rate(rate);

		// Sample playback - startframe & endframe, etc..
		this.pr_setStartAndEndFrames(start_s, dur_s);

		this.pr_setPattern(pattern);
	}


	// NOTE: does not rebuild the pattern, but does set values on a live pattern if it exists
	pr_setStartAndEndFrames {|start_s, dur_s|

		var end_s;

		if(sampleName.notNil) {

		if(start_s.isNil.or { start_s < 0 }) {
			start_s = 0;
		};

		if(start_s.inclusivelyBetween(0, sampleFileDuration).not) {
			"start time % out of bounds %, using 0".format(start_s, sampleFileDuration).error;
			start_s = 0;
		};

		if(dur_s.isNil) { // play from start to end by default
			if(samplePlaybackDuration.isNil) {
				dur_s = sampleFileDuration - start_s;
			} {
				dur_s = samplePlaybackDuration;
			};
		};

		end_s = start_s + dur_s;

		if(end_s.inclusivelyBetween(0, sampleFileDuration).not) {
			"end time out of bounds with duration % and start time %, using %".format(dur_s, start_s, sampleFileDuration).error;
			end_s = sampleFileDuration;
		};

		// convert start and end to frame positions
		sampleStartFrame = start_s * (sampleFileFrames / sampleFileDuration);
		sampleEndFrame = end_s * (sampleFileFrames / sampleFileDuration);
		samplePlaybackDuration = end_s - start_s;

		if(pbindef.notNil) {
			Pbindef(pdefId, \start, sampleStartFrame, \end, sampleEndFrame);
		};

		};

	}

	//************** PARSE & COMPILE SAMPLE PATTERN **************//
	// type can be \amp \dur \pitch
	// if no pattern is specified, and a previous pattern exists
	// then a recompile is performed
	pr_setPattern {|pattern, type=\amp|
		var parsedRaw, parsedByMeasure, pb, smpl;
		var measureIdx=0, measureStepDelta;

		if(pattern.isNil) {
			if(ampPattern.isNil) {
				// set a default pattern if no prior pattern exists
				if(sampleName.isNil) {
					pattern = "----";
				} {
					pattern = "d-";
				};
			} {
				pattern = ampPattern;
			};
			type = \amp;

			"Using default pattern '%'".format(pattern).warn;
		};

		if(type == \amp) {
			ampPattern = pattern;
		};

		if(sampleName.notNil) {
			smpl = Smpl.at(sampleName);
		};

		parsedByMeasure = List.new;
		pattern.stripWhiteSpace().do{|ch|
			var ms;
			ms = parsedByMeasure.at(measureIdx);
			if(ms.isNil) {
				ms = Dictionary.new;
				ms[\stepDelta] = 1;
				ms[\pattern] = "";
				ms[\rawPattern] = "";
				ms[\parsedDegree] = List.new;
				ms[\parsedSmpl] = List.new;
				ms[\parsedDur] = List.new;
				ms[\parsedAmp] = List.new;
				ms[\parsedDelta] = List.new;
				parsedByMeasure.add(ms);
			};

			switch(ch,
				$ , { // space - new measure
					measureIdx = measureIdx + 1;
				},
				$-, { // dash - rest
					ms[\parsedAmp].add(nil);
					ms[\rawPattern] = ms[\rawPattern] ++ $-;
				},
				{ // anything else - an event
					if(type == \amp) {
						if(ampMap.includesKey(ch)) {
							ms[\parsedAmp].add(ch);
							ms[\rawPattern] = ms[\rawPattern] ++ ch;
						} {
							"Invalid amp value '%' in pattern '%'".format(ch, pattern).throw;
						}
					};

					// Pitch pattern
					if(type == \pitch) {

					};

					// Dur pattern
					if(type == \dur) {

					};
				}
			);
		};

		// Go through all measures and update stepDeltas
		parsedByMeasure.do {|ms|
			// 1. calculate stepDelta
			if(fixedStepDelta.isNil) {
				ms[\stepDelta] = 1 / ms[\parsedAmp].size;
			} {
				ms[\stepDelta] = fixedStepDelta;
			};

			// 2. update degree, duration & amp values
			ms[\parsedAmp] = ms[\parsedAmp].collect {|val|
				var res;
				// Side effects first
				ms[\parsedDur].add(ms[\stepDelta] * durationScale);
				ms[\parsedDelta].add(ms[\stepDelta]);
				ms[\parsedDegree].add(0);
				ms[\parsedSmpl].add(sampleName);

				if(val.isNil) {
					res = Rest(ms[\stepDelta]);
				} {
					res = ampMap[val];
				};

				res;
			};

		};


		// Pattern Builder / PBind
		pb = Dictionary.new;
		pb.put( \type, \smpl);
		pb.put( \scale, pitchScale );
		pb.put( \root, zeroDegree );
		pb.put( \octave, zeroOctave );
		pb.put( \pan, eventPanning);
		pb.put( \out, trackBus ); // output is always to the track insert bus

		// Create smpl, dur, degree, delta, amp sequences
		pb.put(\smpl, List.new);
		pb.put(\dur, List.new);
		pb.put(\degree, List.new);
		pb.put(\delta, List.new);
		pb.put(\amp, List.new);

		parsedByMeasure.do {|ms|
			pb[\smpl].add( Pseq(ms[\parsedSmpl].asArray) );
			pb[\dur].add( Pseq(ms[\parsedDur].asArray) );
			pb[\degree].add( Pseq(ms[\parsedDegree].asArray) );
			pb[\delta].add( Pseq(ms[\parsedDelta].asArray) );
			pb[\amp].add( Pseq(ms[\parsedAmp].asArray) );
		};

		pb[\dur] = Pseq(pb[\dur].asArray, inf);
		pb[\degree] = Pseq(pb[\degree].asArray, inf);
		pb[\delta] = Pseq(pb[\delta].asArray, inf);
		pb[\amp] = Pseq(pb[\amp].asArray, inf);


		// \smpl Specific event Keys
		pb[\smpl] = Pseq(pb[\smpl].asArray, inf);
		pb.put(\start, sampleStartFrame );
		pb.put(\end, sampleEndFrame );
		if(smpl.notNil) {
			pb.put(\rootPitch, smpl.rootPitch.f );
		};
		pb.put(\rate, samplePlayRate );


		pbindef = Pbindef(pdefId, *(pb.getPairs));
		patternString = pattern;
		"Successfully built pattern '%'".format(pattern).postln;
		if(Beat.verbose) {
			pb.postln;
		};
	}


	//*************************** MUTATORS ***********************//

	// Set sample id, start point and duration in seconds
	// TODO: can this be set on a running pbindef
	smpl {|sid, start_s, dur_s|
		var smpl;

		if(sid.notNil) {
			smpl = Smpl.at(sid);

			if(smpl.isNil) {
				// TODO: make this more graceful....
				"Sample '%' could not be found".format(sid).throw;
			};

			if((sid != sampleName)) { // a new sample file is being used for the first time

				if((smpl.class != LiveSample).and { Smpl.at(sampleName).class == LiveSample }) {
					// We are switching from a livesample to a regular sample
					if(Beat.verbose) {
						"Converted seq '%' to fixed sample '%' from live '%'".format(id, sid, sampleName).warn;
					};
					liveSampler = false;
				};

				sampleName = sid;
				sampleFileDuration = smpl.duration;
				sampleFileFrames = smpl.numFrames;

				"Changed sample to '%'".format(sampleName).post;
			};

			this.pr_setStartAndEndFrames(start_s, dur_s);
		};

	}

	// Add a pattern
	pattern {|pattern, ptype=\amp|
		this.pr_setPattern(pattern, ptype);
	}

	// Convernience method to set an amplitude pattern
	pat {|pattern|
		this.pr_setPattern(pattern, \amp);
	}





	//((((((((((((((((((((((((LIVE SAMPLING)))))))))))))))))))))))//


	//********** CONVERT THIS SAMPLE SEQUENCE TO A LIVESAMPLER ************//
	// in : the input bus to record from
	// must be followed with a call to .rec or .recStart
	live {|in=0, timeout=30|
		var seq, smpl, sid;

		if(liveSampler.not) { // If this sequence is not configured as a livesampler...
			var smpl, sid;
			sid = "ls_" ++ id; // construct a sample id name

			// 1. Request the live sample buffer from Smpl with id sid
			//    or create one if it doesn't already exist...
			smpl = Smpl.at(sid);
			if(smpl.isNil) {
				smpl = Smpl.prepareLiveSample(sid);
				if(Beat.verbose) {
					"\nAllocating new LiveSample buffer '%': %".format(sid, smpl).warn;
				};
			};

			// 2. update the sequence's sample buffer
			this.smpl(sid);

			liveSampler = true;
			if(Beat.verbose) {
				"\nConverting sequence '%' to live sample '%'".format(id, sid).warn;
			};
		};

		^seq;
	}


	// One shot live sampling...
	// TODO: this and especially the use of catch in Beat.live is a bit messy, needs refactoring
	rec {|in=0, ampthresh=0.01, silencethresh=1, timeout=10|
		if(liveSampler) {
			"About to record with input '%', '%'".format(in, in.class).warn;
			switch(in.class,
				Integer, { // bus id
					"Found int!".warn;
					Smpl.catch(sampleName, in, ampthresh, silencethresh, timeout);
				},
				Ndef, { // ndef
					"Found ndef!".warn;
					Smpl.catch(sampleName, in, ampthresh, silencethresh, timeout);
				},
				Symbol, { // fx unit or sequence id
					var insource;
					"Found symbol!".warn;
					// TODO:
					// 1. check if it's a registered fx unit or sequence or ndef
					// 2. get the output ndef of fx unit (??? sequence track insert ???)
					// 3. Smpl.catch(sampleName, insource, ampthresh, silencethresh, timeout);
					//var sourcebus = in.getOutputBus; // must respond to this
					"Not implemented".throw;
				},
				{ "Invalid input source '%', must be bus number, fx unit or ndef.".format(in).error;  }
			);

		} {
			"Sequence % is not a LiveSampler, ignoring".format(id).error;
		};
		^this;
	}

	// TODO: This doesn't work!
	// Continuous live sampling...
	// in - can be an input bus, an ndef, or a symbol (representing a FX unit or Seq ID)
	//      TODO: right now only input bus works...
	recStart {|in=0, timeout=360|
		if(liveSampler) {
			//Smpl.catchStart(sampleName, in, timeout);
			switch(in.class,
				Integer, { // bus id
					"CatchStart on bus '%'".format(in).warn;
					Smpl.catchStart(sampleName, in, timeout);
				},
				Ndef { // ndef
					"Not implemented".throw;
					//Smpl.catchStart(sampleName, in, timeout);
				},
				Symbol, { // fx unit or sequence id
					var insource;
					// TODO:
					// 1. check if it's a registered fx unit or sequence
					// 2. get the output ndef of fx unit (??? sequence track insert ???)
					// 3. Smpl.catchStart(sampleName, insource, timeout);
					//var sourcebus = in.getOutputBus; // must respond to this
					"Not implemented".throw;
				},
				{
					"Invalid input source '%', must be bus number, fx unit or ndef.".format(in).error;

				}
			);

		} {
			"Sequence % is not a LiveSampler, ignoring".format(id).error;
		};
		^this;
	}

	recStop {
		Smpl.catchStop(sampleName);
		^this;
	}





	/***** GETTERS SETTERS AND ACTIVATORS *******/

	// set start position (in seconds, charcode, frames?) : a float, a string, a SequencedCollection
	//   also can set sample playback duration in seconds : a float
	start {|positions, dur=nil|

		if(positions.class === String) {
			// parse positions into an array
		};

		if(positions.isKindOf(SequenceableCollection)) {
			// array of positions...
		} {
			// fixed value.. should be a number..
			this.pr_setStartAndEndFrames(positions, dur);
		};

	}

	/*
	// gamut transposition (???)
	gtrans {|gpitch=0|
		if(pbindef.notNil) {
			Pbindef(pdefId, \gtranspose, gpitch);
		} {
			"Cannot set gtrans on % - build Pdef first!".format(this.id).throw;
		};
	}

	*/


	// set sample playback duration in seconds : a float
	dur {|dur|
		// recalculate end frame...

	}


	// sample playback rate : a float
	// Note: this does not rebuild the pattern
	rate {|rate|
		if(rate.notNil) {
			samplePlayRate = rate;
		}
		^this;
	}



	// TODO: this method doesn't work, it's old cruft
	//b.get("gong1").setPitch("  1234 ---5 --6- 7-8-", Scale.major, \c2);
	setPitch {|pattern, scale, root|
		var lastPitch, parsed = List.new;

		if(scale.isNil) {
			scale = Scale.major;
		};

		if(root.isNil) {
			root = \c1;
		};

		lastPitch = 0;
		pattern.replace(" ", "").do{|ch|
			if(ch === $-) {
				parsed.add(lastPitch);
			} {
				parsed.add(ch);
			}
		};

		// does this even work?
		pbindef.set(\degree, parsed.pseq(inf), \scale, scale, \root, root.f);
		pitchPatternString = pattern;
	}

}


/*
 ____              _   _       ____
/ ___| _   _ _ __ | |_| |__   / ___|  ___  __ _
\___ \| | | | '_ \| __| '_ \  \___ \ / _ \/ _` |
 ___) | |_| | | | | |_| | | |  ___) |  __/ (_| |
|____/ \__, |_| |_|\__|_| |_| |____/ \___|\__, |
       |___/                                 |_|
*/
SynthSequence : EventSequence {

	var <instrumentName;
	var <pitchFactor;


	*new {|serv, parent, synthgroup, seqid, instname, pattern=nil, zeropitch, durscale, outbus, pan, notescale|
		^super.new(serv, parent, synthgroup, seqid, outbus).init(instname, pattern, zeropitch, durscale, outbus, pan, notescale);
	}

	init {|instname, pattern, zeropitch, durscale, outbus, pan, notescale|
		this.pr_update(instname, pattern, zeropitch, durscale, outbus, pan, notescale);
	}

	// Update the currently running Synth Sequencer
	pr_update {|instname, pattern, zeropitch, durscale, outbus, pan, notescale|
		var isNewInstrument;
		isNewInstrument = (instname != instrumentName);

		if(isNewInstrument) {
			this.instrument(instname);
		};

		// rootpitch defaults to \c5 > this is degree: 0 root: 0 octave: 5 in the
		// update track insert destination
		this.out(outbus);

		// melody params
		this.zeroPitch(zeropitch);
		this.scale(notescale);

		this.dur(durscale);
		this.pan(pan);


		this.pr_setPattern(pattern);
	}


	// INSTRUMENT: Parse text pattern into pbindef
	pr_setPattern {|pattern, type=\PAT_SCALEDEGREE|
		var parsedRaw, parsedByMeasure, pb, smpl;
		var measureIdx=0, measureStepDelta;

		parsedByMeasure = List.new;

		pattern.stripWhiteSpace().do{|ch|
			var ms;
			ms = parsedByMeasure.at(measureIdx);
			if(ms.isNil) {
				ms = Dictionary.new;
				ms[\stepDelta] = 1;
				ms[\pattern] = "";
				ms[\rawPattern] = "";
				ms[\parsedDegree] = List.new;
				ms[\parsedDur] = List.new;
				ms[\parsedAmp] = List.new;
				ms[\parsedDelta] = List.new;
				parsedByMeasure.add(ms);
			};

			switch(ch,
				$ , { // new measure
					measureIdx = measureIdx + 1;
				},
				$-, { // rest
					ms[\parsedDegree].add(nil);
					ms[\rawPattern] = ms[\rawPattern] ++ $-;
				},
				{ // event
					if(type == \PAT_SCALEDEGREE) {
						if(scaleDegreeMap.includesKey(ch)) {
							ms[\parsedDegree].add(ch);
							ms[\rawPattern] = ms[\rawPattern] ++ ch;
						} {
							"Invalid scale degree '%' in pattern '%'".format(ch, pattern).throw;
						}
					};
				}
			);
		};

		// Go through all measures and update stepDeltas
		parsedByMeasure.do {|ms|
			// 1. calculate stepDelta
			ms[\stepDelta] = 1 / ms[\parsedDegree].size;

			// 2. update degree, duration & amp values
			ms[\parsedDegree] = ms[\parsedDegree].collect {|val|
				var res;
				// Side effects first
				ms[\parsedDur].add(ms[\stepDelta] * durationScale);
				ms[\parsedDelta].add(ms[\stepDelta]);
				ms[\parsedAmp].add(1.0);

				if(val.isNil) {
					res = Rest(ms[\stepDelta]);
				} {
					res = val.digit;
				};

				res;
			};

			// 3. create a Pbind for this measure
			// TODO: Research is this design pattern useful in any way?
			//    does it allow dynamic shuffling of patterns?
			// see guide on Composing Patterns
			// using EventPatternProxy ~rhythm ~melody example..
			ms[\pbind] = Pbind(
				\degree, Pseq(ms[\parsedDegree], 1),
				\amp, Pseq(ms[\parsedAmp], 1),
				\dur, Pseq(ms[\parsedDur], 1),
			);
		};

		// Build pbind
		pb = Dictionary.new;
		pb.put(\type, \note);
		pb.put(\instrument, instrumentName);

		// Create dur, degree, delta, amp sequences
		pb.put(\dur, List.new);
		pb.put(\degree, List.new);
		pb.put(\delta, List.new);
		pb.put(\amp, List.new);

		parsedByMeasure.do {|ms|
			pb[\dur].add( Pseq(ms[\parsedDur]) );
			pb[\degree].add( Pseq(ms[\parsedDegree]) );
			pb[\delta].add( Pseq(ms[\parsedDelta]) );
			pb[\amp].add( Pseq(ms[\parsedAmp]) );
		};

		pb[\dur] = Pseq(pb[\dur], inf);
		pb[\degree] = Pseq(pb[\degree], inf);
		pb[\delta] = Pseq(pb[\delta], inf);
		pb[\amp] = Pseq(pb[\amp], inf);

		pb.put( \scale, pitchScale );
		pb.put( \root, zeroDegree );
		pb.put( \octave, zeroOctave );

		pb.put( \pan, eventPanning );

		pb.put( \out, trackBus ); // should always be the track insert bus

		if(Beat.verbose) {
			"Parsed Pattern Keys ...".warn;
			pb.keysValuesDo {|key, val|
				"%: %".format(key, val).postln;
			}
		};

		pbindef = Pbindef(pdefId, *(pb.getPairs));
		patternString = pattern;

		"Built pattern: %".format(pattern).warn;
	}


	// TODO: would be really useful to have a command like this!
	//    just to update the scale/root/pattern
	setPitch {|pattern, scale, root|
		var lastPitch, parsed = List.new;

		if(scale.isNil) {
			scale = Scale.major;
		};

		if(root.isNil) {
			root = \c1;
		};

		lastPitch = 0;
		pattern.replace(" ", "").do{|ch|
			if(ch === $-) {
				parsed.add(lastPitch);
			} {
				parsed.add(ch);
			}
		};

		pbindef.set(\degree, parsed.pseq(inf), \scale, scale, \root, root.f);
		pitchPatternString = pattern;
	}

	//***** PUBLIC API *****//

	// set instrument name : a synth symbol in SynthDescLib.global (Symbol)
	instrument {|instname|
		if(SynthDescLib.global.synthDescs.at(instname).notNil) {
			instrumentName = instname;
		} {
			"Could not find synth definition '%'".format(instrumentName).throw;
		};
		^this;
	}

	// Set a scale degree pattern
	pat {|pattern|
		this.pr_setPattern(pattern, \PAT_SCALEDEGREE);
	}




}



// Some helpful utilities for working with beats and samples
// especially for samples that are fragments of larger sample files...
SmplHelper {

	var <sampleList;
	var <samplesByName;

	*new {|slist|
		^super.new.init(slist);
	}

	// Preloads samples in the list and gives some useful functions for playing them...
	// slist is an array of samples in the format
	// [ SampleHelper_ID, Smpl_ID, numChannels, start, end ]
	init {|slist|
		sampleList = slist;
		Smpl.preload(sampleList.collect(_.at(1)));
		samplesByName = Dictionary.new;
		sampleList.do({|it| samplesByName.put(it[0], it) });
	}

	// Make a beatseq using the start and stop positions stored in the sample list
	sq {|beat, name, sidx, pattern, stepdur, sampledur, out, rate, pan|
			var sm;
			if(sidx.class === String) {
				sm = samplesByName.at(sidx);
			} {
				sm = sampleList[sidx];
			};
			beat.samSeq(name, sm[1], pattern, stepdur, sampledur, out, sm[3], sm[4], rate, pan);
	}

	// one shot sample player, uses start and stop frames
	shot {|sidx, out=0, rate=1.0, amp=1.0, co=18000, rq=0.5, pan=0, loops=1|
		var sm;
		if(sidx.class === String) {
			sm = samplesByName.at(sidx);
		} {
			sm = sampleList[sidx];
		};

		if(sm.notNil) {
			Smpl.splay(sm[1], sm[3], sm[4], rate, amp, out, co, rq, pan, loops);
		} {
			"No sample '%' was found".format(sidx).error;
		};
	}

	// get Smpl id for entry
	id {|sidx|
		var sm;
		if(sidx.class === String) {
			sm = samplesByName.at(sidx);
		} {
			sm = sampleList[sidx];
		};

		if(sm.notNil) {
			^sm[1];
		} {
			"No sample '%' was found".format(sidx).error;
		};
	}

	// more flexible wrapper around Smpl.splay
	pl {|sidx, start=0, end=(-1), out=0, rate=1.0, amp=1.0, co=20000, rq=1.0, pan=0.0, loops=1|
		var sm;
		if(sidx.class === String) {
			sm = samplesByName.at(sidx);
		} {
			sm = sampleList[sidx];
		};

		if(sm.notNil) {
			Smpl.splay(sm[1], start, end, rate, amp, out, co, rq, pan, loops);
		} {
			"No sample '%' was found".format(sidx).error;
		};

	}


}





