/*
FX Units Livecoda
(c) 2021 Jonathan Reus
*/

/*
@usage
f = FX.new(s, Group.new(s, \addAfter));
f.unit(\fx2, 2, "rev(vs100 lo0.2 mi0.5 hi0.1 mx0.7)");
f.unit(\fx1, 2, "del() ws() rev()");


FX.setUnit("fx1", "ws rev");
b.syn("s1", \karp, "0 1 2 3", out: FX.bus("fx1"));

FX Unit Microlanguage
See FXHelp.help()

ws rev
ws(t1 p20 v2 mx0.8) rev(vt10.5 vs2 er0.7 lo0.2 mi0.5 hi0.4 mx0.5) ws(t2 p5 v1 mx0.5)  lpf(co1200 cv200 rq0.7)
ws(p20 mx0.8) rev(mx0.5) ws(t2 p10 v1 mx0.5) hpf(co1200)
ws(p10 v1 mx0.99) rev(er0.8 mx0.9) ws(t2 p5 v1 mx0.9) lpf(co1200)


*/

FXHelp {

	// Microlang help string
	*help {
		"
ws: waveshaping distortion
ty=type (1:tanh, 2:softclip)
pr=pre amplification (float)
pv=pre amplification variation (float)
vt=preamp modulation type (1:LFNoise2 2:SinOsc)
vr=preamp modulation rate (float)
mx=wet/dry mix (0-1)

lpf: filter
co=cutoff hz (float)
cv=cutoff variation hz (float)
rq=reciprocal of q (float)
vt=cutoff modulator type (1: LFNoise2, 2: SinOsc)
vr=cutoff modulator rate hz (float)

hpf: filter
co=cutoff hz (float)
cv=cutoff variation hz (float)
rq=reciprocal of q (float)
vt=cutoff modulator type (1: LFNoise2, 2: SinOsc)
vr=cutoff modulator rate hz (float)

gn:  gain stage
lv=gain level (float: 0-1)

in: mono signal mic input (SoundIn)
bs=index of audio input bus (int)
pr=pre amplification (float)
pn=signal panning (float)

xin: arbitrary bus input (In)
bs=bus number (int)
ch=channels (int)
pr=pre amplification (float)
pn=signal panning (float) - mixes multichannel down to mono

out: signal output, number of channels matches whatever signal is at that point in the chain
bs=ouput bus number (int)
pr=pre amplification (float)

del: delay
dt=delay time in seconds (float)
dy=decay time in seconds (float)
mx=dry/wet mix (float)

rev: reverb JPverb
vt=reverb time in seconds, does not effect early reflections
dm=hf damping as verb decays (0-1.0) - 0 is no damping
vs=reverb size, scales size of delay lines (0.5 - 5) - values below 1 sound metallic
er=early reflection shape (0-1.0) 0.707 = smooth exp decay / lower value = slower buildup
lo=reverb time multiplier in low frequencies (0-1.0)
mi=reverb time multiplier in mid frequencies (0-1.0)
hi=reverb time multiplier in hi frequencies (0-1.0)
mx=wet/dry mix (0-1)

cmp: compressor (Compander)
th=amplitude threshhold for compression to kick in (0.0-1.0)
ra=compression ratio (0.0-1.0) - 1.0 is no compression, 0.333 is a ratio of 1/3
at=attack in seconds
re=release in seconds
s_=side chain signal, either a fx unit name or ndef name
sb=side chain signal bus
sl=side chain level (0.0-1.0)
mx=wet/dry mix (0.0-1.0)

".postln;
	}

}




/* FX MANAGER */
FX : SymbolProxyManager {
	var <server;
	var <fxgroup; // should be at the end of the server's group structure
	var <units;
	var <cleanbus; // send clean signals here
	var <fxbus; // used internally to send from fxmixproxy to masterSynth
	var <fxmixproxy;
	var <mastersynth; // final signal mixing, limiting & volume stage before audio hardware

	classvar <>verbose=true;
	classvar <singleton;

	*new {|serv, group|
		if(singleton.isNil) {
			^super.new.init(serv, group);
		} {
			^singleton;
		}
	}

	*getManager {
		if(singleton.isNil) {
			"FX Manager is not initialized, use FX.new(server, group) to create the singleton instance".error;
		};
		^singleton;
	}

	getManager {
		^this;
	}

	// Usually you don't want to mess with the master outBus and gain
	//   but sometimes you might want to, for example, sending the signal to an
	//   external app via a virtual audio bus
	master {|outbus=0, gain=1.0|
		if(this.mastersynth.notNil) {
			this.mastersynth.set(\outbus, outbus, \gain, gain);
		};
	}

	init {|serv, group|
		server = serv;

		"Setting Ndef.defaultServer to '%'".format(server).warn;
		Ndef.defaultServer = server;

		if (group.isNil) {
			group = Group.new(server, \addAfter);
		};

		fxgroup = group;
		"Found fx group %".format(fxgroup).warn;

		this.loadSynthDefsEventTypes();


		units = Dictionary.new;
		cleanbus = Bus.audio(server, 2);
		fxbus = Bus.audio(server, 2);
		fxmixproxy = NodeProxy.new(server, \audio, 2);
		//fxmixproxy.setGroup(fxgroup);
		fxmixproxy.play(out: fxbus, numChannels: 2, group: fxgroup, multi: true, addAction: \addToHead);
		singleton = this;

		// Create master node
		// mastersynth = {|gain=1.0, fxbus, cleanbus, outbus=0|
		// 	var mix, fxsig, cleansig;
		// 	cleansig = In.ar(cleanbus, 2);
		// 	fxsig = In.ar(fxbus, 2);
		// 	mix = cleansig+fxsig;
		// 	mix = LeakDC.ar(Limiter.ar(mix, 1.0, 0.001));
		// 	//Out.ar(outbus, mix * gain);
		// 	mix * gain;
		// }.play(target: server, outbus: 0, addAction: \addToTail);



		{
			server.sync;
			if(mastersynth.isNil) {
				"FX: Creating master node".postln;
				mastersynth = Synth(\mixMaster, [
					\fxbus, fxbus,
					\cleanbus, cleanbus
				], fxgroup, \addToTail);

			};
		}.fork(AppClock);

	}


	loadSynthDefsEventTypes {
		SynthDef(\mixMaster, {|gain=1.0, fxbus, cleanbus, outbus=0|
			var mix, fxsig, cleansig;
			cleansig = In.ar(cleanbus, 2);
			fxsig = In.ar(fxbus, 2);
			mix = cleansig+fxsig;
			mix = LeakDC.ar(Limiter.ar(mix, 1.0, 0.001));
			Out.ar(outbus, mix * gain);
		}).add;
	}

	at {|name|
		^units.at(name);
	}

	// Get the input bus of an FXUnit by name
	// this input bus can be used as the output of synths to go into the FX unit
	bus {|name|
		var unit = units.at(name);
		var res;
		if(unit.notNil) {
			res = unit.inBus;
		} {
			"No FX unit '%' found".format(name).error;
		};
		^res;
	}

	// Remove a given unit by name and free its resources....
	freeUnit {|name|
		var unit = units.at(name);
		if(unit.notNil) {
			this.unregister(unit);
			unit.freeUnit; // free resources...
		} {
			"FX Unit '%' does not exist".format(name).error;
		}
	}

	// convenience alias for freeUnit
	xx {|name|
		this.freeUnit(name);
	}

	// Unregister a FXUnit from being managed by this FX manager
	unregister {|fxunit|
		if(units.at(fxunit.id).isNil) {
			"FX.unregister: Attempted to unregister '%' but this FX Unit name is not registered".format(fxunit.id).error;
		} {
			units.removeAt(fxunit.id);
			SymbolProxyManager.unregisterSymbolProxy(fxunit.id);
		}

	}

	// Register a FX Unit to be managed by this FX manager
	register {|fxunit|
		if(units.at(fxunit.id).isNil) {
			units.put(fxunit.id, fxunit);
			SymbolProxyManager.registerSymbolProxy(fxunit.id, FX);
		} {
			"FX.register: Attempted to register '%' but this FX Unit is already registered ... ignoring".format(fxunit.id).error;
		}
	}



	// Allocate the data structure for a new FX unit but do not compile the Ndef
	allocUnit {|name, inChannels=2|
		var unit;

		if(name.class != Symbol) {
			"FX Unit name must be a symbol, received %: '%'".format(name.class, name).throw;
		};

		unit = units.at(name);

		if(unit.isNil) {
			"NEW UNIT '%' '%'".format(name, inChannels).postln;
			unit = FXUnit.new(name, this, inChannels, server);
			this.register(unit);
		} {
			"FX Unit '%' already exists and will not be allocated".format(name).error;
		}
		^unit;
	}

	// Set the structure of the FXUnit and compile the NDef
	// desc fits a fx unit description microlanguage statement
	setUnit {|name, desc|
		var unit = units.at(name);
		if(unit.notNil) {
			unit.pr_parse(desc);
			unit.pr_buildNdef;
		} {
			"FX Unit '%' not found".format(name).error;
		};
		^unit;
	}


	// Convenience method, combines allocUnit (when needed) and setUnit
	unit {|name, inChannels=2, desc|
		var theunit = units.at(name);
		if(theunit.isNil) {
			this.allocUnit(name, inChannels);
		};
		^this.setUnit(name, desc);
	}







}











/* FX UNIT
_______  __  _   _ _   _ ___ _____
|  ___\ \/ / | | | | \ | |_ _|_   _|
| |_   \  /  | | | |  \| || |  | |
|  _|  /  \  | |_| | |\  || |  | |
|_|   /_/\_\  \___/|_| \_|___| |_|

*/

FXUnit {
	var <id;
	var <parentFX;
	var <ndefid;
	var <inChannels;
	var <inBus;
	var <inputs;
	var <gain;
	var <code;

	var <ndef;
	var <>fadeTime = 0.02;
	var <fxStagesChain;  // List : stages in order of signal flow
	var <fxStagesByType; // Dictionary : stages indexed by type

	classvar <stageDescs;
	classvar <stageRegex;

	*initClass {
		// Populate stageDescs with format descriptions of each stage type
		stageDescs = Dictionary.newFrom([
			// audio sound in, bus index, preamp, panning
			\in, Dictionary.newFrom([\bs, [Integer, 0], \pr, [Float, 1.0], \pn, [Float, 0.0]]),

			// arbitrary audio bus out, bus output, preamp
			\out, Dictionary.newFrom([\bs, [Integer, 0], \pr, [Float, 1.0]]),

			// arbitrary audio bus input: bus index, num channels, preamp, pan (mix multich to mono if set)
			\xin, Dictionary.newFrom([\bs, [Integer, 0], \ch, [Integer, 2], \pr, [Float, 1.0],
				\pn, [Float, nil]]),

			\gn, Dictionary.newFrom([\lv, [Float, 1.0]]),

			// distortion type, preamp, preamp_variation, preamp_var_type, preamp_var_rate, mix
			\ws, Dictionary.newFrom([\ty, [Integer, 1], \pr, [Float, 20], \pv, [Float, 2],
				\vt, [Integer, 1], \vr, [Float, 1], \mx, [Float, 0.8]]),

			// cutoff (hz), co_variation (hz), co_var_type, co_var_rate, recip Q,
			\lpf, Dictionary.newFrom([\co, [Float, 1200], \cv, [Float, 200], \vt, [Integer, 1],
				\vr, [Float, 0.5], \rq, [Float, 0.7]]),

			// cutoff (hz), co_variation (hz), co_var_type, co_var_rate, recip Q,
			\hpf, Dictionary.newFrom([\co, [Float, 3000], \cv, [Float, 1000], \vt, [Integer, 1],
				\vr, [Float, 1], \rq, [Float, 0.5]]),

			// delay time, decay, mix
			\del, Dictionary.newFrom([\dt, [Float, 0.5], \dy, [Float, 0.5], \mx, [Float, 0.5]]),

			// verb time, verb damping, verb size, early reflection shape,
			// lows time, mids time, his time, mix
			\rev, Dictionary.newFrom([\vt, [Float, 10.5], \dm, [Float, 0.0],
				\vs, [Float, 2.0],
				\er, [Float, 0.7], \mx, [Float, 0.5],
				\lo, [Float, 0.2], \mi, [Float, 0.5], \hi, [Float, 0.5]]),

			// amp threshhold, comp ratio, comp attack, comp release
			// side chain signal, side chain level, wet/dry mix
			\cmp, Dictionary.newFrom([\th, [Float, 0.5], \ra, [Float, 1/3],
				\at, [Float, 0.01], \re, [Float, 0.1],
				\s_, [Symbol, nil], \sb, [Integer, nil], \sl, [Float, 1.0], \mx, [Float, 1.0]]),


		]);

		// Build regexp patterns for each stage's params
		stageDescs.keysValuesDo {|stageName, stageSpec|
			stageSpec[\paramRegex] = "";
			stageSpec.keysValuesDo {|paramName, paramSpec|
				if(stageSpec[\paramRegex] != "") {
					stageSpec[\paramRegex] = stageSpec[\paramRegex] ++ "|";
				};
				stageSpec[\paramRegex] = stageSpec[\paramRegex] ++ paramName.asString;
			};
			stageSpec[\paramRegex] = "(%)([\\.0-9a-zA-Z_]+)".format(stageSpec[\paramRegex]);
		};

		// Build general FX stage parsing regex
		stageRegex = "";
		stageDescs.keysValuesDo {|stageName, desc|
			if(stageRegex != "") {
				stageRegex = stageRegex ++ "|";
			};
			stageRegex = stageRegex ++ stageName.asString;
		};
		stageRegex = "(%|[a-zA-Z0-9_]+)(\\([\ \\.a-zA-Z_0-9]*\\))".format(stageRegex);

		"FX: Compiled stage regex: '%'".format(stageRegex).warn;
	}


	*new {|name, parent, numInputChannels|
		^super.new.pr_init(name, parent, numInputChannels);
	}

	pr_init {|name, parent, numInputChannels|
		id = name;
		parentFX = parent;
		ndefid = ("fxunit_"++name).asSymbol;
		inChannels = numInputChannels;
		inBus = Bus.audio(parentFX.server, inChannels);
		inputs = Dictionary.new; // input processes to this unit
		gain = 1.0;

		code = Dictionary.new;

		// Code for a simple passthrough
		// TODO: In or InFeedback ? Make this an optional argument... ?
		//code[\head] = "var sig, v1, v2, v3; sig = InFeedback.ar('inbus'.kr(%), %) * 'inamp'.kr(1.0, 0.5);".format(inBus.index, inChannels);
		code[\head] = "var sig, v1, v2, v3; sig = In.ar('inbus'.kr(%), %) * 'inamp'.kr(1.0, 0.5);".format(inBus.index, inChannels);
		code[\dsp] = List.new;
		code[\tail] = "sig * 'outamp'.kr(1.0);";
	}


	// Parse a DSP description into code[\dsp]
	//   TODO: REFACTOR! There are surely ways of doing this more elegantly with less biolerplate!
	pr_parse {|desc|
		var lex, post, idx, i=0, j=0, regexp;
		var dsp; // this will be populated line by line with the dsp code
		var ndef, ndefid;
		var dlag = 0.0; // default lag
		var chain = List.new; // ordered list of stages
		var stagesByType = Dictionary.new; // keeps track of multiple stages of same type

		// Lex/parse separate stages
		lex = desc.stripWhiteSpace.findRegexp(FXUnit.stageRegex);

		if(FX.verbose) {
			"Lex (%): \n   %".format(FXUnit.stageRegex, lex).postln;
		};

		// FIRST PASS: automatic lexical parsing of fx stage params based on FX.stageDescs
		//             also checks for "boutique" stages in the form of ndefs...
		idx=0;
		while { idx < lex.size } { // for each stage...
			var paramidx, paramlex, codebuilder;
			var stageType = lex[idx+1][1].asSymbol; // get the stage name
			var newStage, stageList, stageIndex;
			var stageDesc = FXUnit.stageDescs.at(stageType);

			if(FX.verbose) {
				"Parsing... stage '%'".format(stageType).postln;
			};

			if(stageDesc.isNil) {
				// Check if the stage name is actually an ndef..
				//    if it is, then add that ndef as a stage to the chain code..
				if( Ndef.dictFor(parentFX.server).existingProxies.includes(stageType) ) {
					if(FX.verbose) { "Found ndef '%' in FX Unit %".format(stageType, id).warn };

					// so how do we represent the ndef in the chain / stagesByType?
					stageList = stagesByType.at(\ndef);
					if(stageList.isNil) {
						stageList = List.new;
						stagesByType.put(\ndef, stageList);
						stageIndex = 0;
					} {
						stageIndex = stageList.size;
					};

					newStage = Dictionary.newFrom([\type, \ndef, \id, stageType, \idx, stageIndex]);
					stageList.add(newStage);
					chain.add(newStage);

				} { // If not, throw a lang error..
					LangError("Invalid FX stage or ndef name '%'".format(stageType)).throw;
				};

			} {
				paramlex = lex[idx+2][1].findRegexp(stageDesc[\paramRegex]);
				paramidx = 0;
				stageList = stagesByType.at(stageType);
				if(stageList.isNil) {
					stageList = List.new;
					stagesByType.put(stageType, stageList);
					stageIndex = 0;
				} {
					stageIndex = stageList.size;
				};

				// INITIALIZE new Stage
				newStage = Dictionary.new;
				newStage.put(\type, stageType); newStage.put(\idx, stageIndex);
				stageList.add(newStage);
				chain.add(newStage);
				stageDesc.keysValuesDo {|paramName, paramSpec|
					if(paramName != \paramRegex) {
						newStage.put(paramName, paramSpec[1]); // get the default value
					};
				};

				// Fill in actual param values out of the parameter list...
				while { paramidx < paramlex.size } {
					var paramName, paramVal, paramDesc;

					paramName = paramlex[paramidx+1][1].asSymbol;
					paramVal = paramlex[paramidx+2][1];
					paramDesc = stageDesc[paramName];
					//if(FX.verbose) { "Read param '%' with val '%' and desc '%'".format(paramName, paramVal, paramDesc).warn;  };
					switch(paramDesc[0],
						Integer, {
							if(paramVal.isInteger) {
								paramVal = paramVal.asInteger;
							} {
								LangError("Invalid int param value '%' '%'".format(paramName, paramVal)).throw;
							};

						},
						Float, {
							if(paramVal.isFloat) {
								paramVal = paramVal.asFloat;
							} {
								LangError("Invalid float param value '%' '%'".format(paramName, paramVal)).throw;
							};
						},
						Symbol, { paramVal = paramVal.asSymbol },
						{ LangError("Invalid param desc '%'".format(paramDesc)).throw; }
					);

					newStage[paramName] = paramVal;
					paramidx = paramidx+3; // next parameter in paramlex...
				}

			};

			idx=idx+3; // next stage

		}; // ...END FIRST PASS...

		// SECOND PASS: translate parsed stages into UGen code....
		// TODO: Maybe put this into its own method? Or into buildNdef?
		dsp = List.new;
		chain.do {|st|
			var sid = st[\type].asString ++ st[\idx].asString;

			switch(st[\type],
				\ndef, { // NDEF
					var ndefname = st[\id];
					"Parsing Ndef '%'".format(ndefname).warn;
					dsp.add("sig = sig + Ndef.ar('%');".format(ndefname));
				},

				\in, { // MONO SOUNDIN INPUT bs, pr, pn
					var inbus = st[\bs], preamp = st[\pr], pan = st[\pn];
					dsp.add("sig = sig + Pan2.ar(SoundIn.ar('%_bs'.kr(%)), '%_pn'.kr(%), '%_pr'.kr(%));".format(sid, inbus, sid, pan, sid, preamp));
				},

				\xin, { // ARBITRARY AUDIO BUS INPUT: bs, ch, pr, pn
					var inbus = st[\bs], numchans = st[\ch], preamp = st[\pr], pan = st[\pn];

					if(numchans == 1) { // mono
						if(pan.isNil) { pan = 0.0 };
						dsp.add("sig = sig + Pan2.ar(InFeedback.ar('%_bs'.kr(%), %), '%_pn'.kr(%), '%_pr'.kr(%));".format(sid, inbus, numchans, sid, pan, sid, preamp));
					} { // multichannel
						if(pan.isNil) {
							// No pan control, use multichannel signal as-is
							dsp.add("sig = sig + (InFeedback.ar('%_bs'.kr(%), %) * '%_pr'.kr(%));".format(sid, inbus, numchans, sid, preamp));
						} {
							// Multichan mixed to mono, then panned
							dsp.add("sig = sig + Pan2.ar(InFeedback.ar('%_bs'.kr(%), %).sum, '%_pn'.kr(%),  '%_pr'.kr(%));".format(sid, inbus, numchans, sid, pan, sid, preamp));

						};
					};

				},
				\out, { // OUTPUT bs, pr
					var outbus = st[\bs], preamp = st[\pr];
					dsp.add("Out.ar('%_bs'.kr(%), sig * '%_pr'.kr(%));".format(sid, outbus, sid, preamp));
				},
				\gn, { // GAIN lv
					var gainlevel = st[\lv];
					dsp.add("sig = sig * '%_mx'.kr(%);".format(sid, gainlevel));
				},
				\ws, { // WAVESHAPER ty,pr,pv,vt,vr,mx
					var ws_type = st[\ty], preamp = st[\pr], prevar = st[\pv];
					var prevar_rate = st[\vr], prevar_type = st[\vt], mix = st[\mx];
					if([1,2].includes(ws_type)) {
						if([1,2].includes(prevar_type)) {
							var distfunc, varfunc;
							dsp.add("v1 = Amplitude.ar(sig, 0.001, 0.01);"); // ??? why ??? to adjust after amp?

							// NOTE: varfunc and distfunc cannot be changed without recompiling the ndef
							varfunc = ["LFNoise2.ar('%_vr'.kr(%), mul: '%_pv'.kr(%))",
								"SinOsc.ar('%_vr'.kr(%), mul: '%_pv'.kr(%))"][prevar_type-1];
							varfunc = varfunc.format(sid, prevar_rate, sid, prevar);
							dsp.add("v2 = (sig * ('%_pr'.kr(%) + %));".format(sid, preamp, varfunc));

							distfunc = ["tanh", "softclip"][ws_type-1];
							dsp.add("sig = (v2.% * v1).madd('%_mx'.kr(%)) + sig.madd(1 - '%_mx'.kr);".format(distfunc, sid, mix, sid));

						} { LangError("Invalid preamp variation type % to ws".format(prevar_type)).throw };
					} { LangError("Invalid waveshaper type '%'".format(ws_type)).throw };
				},
				\lpf, { // LOW PASS FILTER co, cv, vt, vr, rq
					var cutoff = st[\co], covar = st[\cv], rq = st[\rq];
					var covar_type = st[\vt], covar_rate = st[\vr];

					if([1,2].includes(covar_type)) {
						var varfunc;
						varfunc = ["LFNoise2.ar('%_vr'.kr(%), mul: '%_cv'.kr(%))", "SinOsc.ar('%_vr'.kr(%), mul: '%_cv'.kr(%))"][covar_type-1];
						varfunc = varfunc.format(sid, covar_rate, sid, covar);

						dsp.add("sig = BLowPass4.ar(sig, '%_co'.kr(%) + %, '%_rq'.kr(%));".format(sid, cutoff, varfunc, sid, rq));

					} { LangError("Invalid cutoff variation type '%'".format(covar_type)).throw; };
				},
				\hpf, { // HI PASS FILTER co, cv, vt, vr, rq
					var cutoff = st[\co], covar = st[\cv], rq = st[\rq];
					var covar_type = st[\vt], covar_rate = st[\vr];

					if([1,2].includes(covar_type)) {
						var varfunc;
						varfunc = ["LFNoise2.ar('%_vr'.kr(%), mul: '%_cv'.kr(%))", "SinOsc.ar('%_vr'.kr(%), mul: '%_cv'.kr(%))"][covar_type-1];
						varfunc = varfunc.format(sid, covar_rate, sid, covar);

						dsp.add("sig = BHiPass4.ar(sig, '%_co'.kr(%) + %, '%_rq'.kr(%));".format(sid, cutoff, varfunc, sid, rq));

					} { LangError("Invalid cutoff variation type '%'".format(covar_type)).throw; };

				},
				\del, { // DELAY dt, dy, mx
					var delaytime=st[\dt], decaytime=st[\dy], mix=st[\mx];
					dsp.add("sig = CombL.ar(sig, '%_dt'.kr(%), '%_dt'.kr, '%_de'.kr(%), mul: '%_mx'.kr(%)) + (sig * (1 - '%_mx'.kr));".format(sid, delaytime, sid, sid, decaytime, sid, mix, sid));
				},
				\rev, { // REVERB vt, dm, vs, er, mx, lo, mi, hi
					var verbtime=st[\vt], damping=st[\dm], verbsize=st[\vs];
					var earlyref=st[\er], lows=st[\lo], mids=st[\mi], highs=st[\hi], mix=st[\mx];
					dsp.add("v1 = JPverb.ar(sig, '%_vt'.kr(%), '%_da'.kr(%), '%_vs'.kr(%), '%_er'.kr(%), 0.8, 0.9, '%_lo'.kr(%), '%_mi'.kr(%), '%_hi'.kr(%), 200, 3000);".format(sid, verbtime, sid, damping, sid, verbsize, sid, earlyref, sid, lows, sid, mids, sid, highs));
					dsp.add("sig = v1.madd('%_mx'.kr(%)) + sig.madd( 1 - '%_mx'.kr);".format(sid, mix, sid));

				},

				\cmp, { // COMPRESSOR th, ra, at, re, s_, sb, sl, mx
					var thresh=st[\th], ratio=st[\ra], attack=st[\at], release=st[\re];
					var sidechain_sig=st[\s_], sidechain_bus=st[\sb], sidechain_level=st[\sl];
					var wetdrymix=st[\mx];
					var control;

					if (sidechain_bus.notNil) { // SIDECHAIN COMPRESSION via input audio bus
						control = "InFeedback.ar('%_sb'.kr(%))".format(sid, sidechain_bus);
					};

					if (sidechain_sig.notNil) { // SIDECHAIN COMPRESSION via ndef or fxunit
						var ndefname, fxunit;
						fxunit = parentFX.at(sidechain_sig);
						if( fxunit.notNil ) { // 1. check if sidechain is a fx bus name... if so, grab its ndef
							ndefname = fxunit.ndefid;
						} {
							ndefname = sidechain_sig;
						};

						if( Ndef.dictFor(parentFX.server).existingProxies.includes(ndefname) ) {
							// 2. check for ndef name
							if(FX.verbose) { "Found ndef '%' for sidechain compressor input in FX Unit '%'".format(ndefname, id).warn };
							control = "Ndef('%')".format(ndefname);
						} {
							// 3. sidechain is neither FX Unit nor Ndef.. throw error
							LangError("Invalid FX unit or ndef name in sidechain cmp: '%'".format(ndefname)).throw;
						};

					};

					if (control.isNil) { // REGULAR COMPRESSION
						control = "sig";
					};

					dsp.add("v1 = Compander.ar(sig, % * '%_sl'.kr(%), '%_th'.kr(%), 1.0, '%_ra'.kr(%), '%_at'.kr(%), '%_re'.kr(%));".format(control, sid, sidechain_level, sid, thresh, sid, ratio, sid, attack, sid, release));
					dsp.add("sig = v1.madd('%_mx'.kr(%)) + sig.madd( 1 - '%_mx'.kr );".format(sid, wetdrymix, sid));

				},

				{ LangError("Invalid DSP stage named '%'".format(st[\type])).throw; }
			);

		}; // END SECOND PASS


		// 2. UPDATE DSP DEFINITION CODE
		if(dsp.size > 0) {
			code[\dsp] = dsp;
		};

		// 3. UPDATE STATIC FX CHAIN DESCRIPTIONS
		fxStagesChain = chain;
		fxStagesByType = stagesByType;

	}

	// TODO: might be better to have some kind of intermediary representation for
	//       DSP stages, which then get translated to ndef code in this method...
	// Build/rebuild the Ndef from code segments
	pr_buildNdef {

		// TODO: really need to check if parameters of the ndef already have
		//   values that are nodeproxies ... because if we set the values of these
		//   params to something new when recompiling the ndef, these nodeproxies will
		//   become orphaned and need to be freed (I think?)

		var firstBuild, ndefbuilder = "Ndef('%', { % % % })".format(
			ndefid,
			code[\head],
			code[\dsp].join($ ),
			code[\tail]
		);

		firstBuild = ndef.isNil;

		if(FX.verbose) {
			"Compiling FX microlang into Ndef:\n".warn;
			ndefbuilder.postln;
		};


		if(firstBuild == true) {

			ndef = ndefbuilder.interpret;
			parentFX.fxmixproxy.add(ndef, 0);
			ndef.fadeTime = fadeTime;
		} {
			ndef.fadeTime = fadeTime;
			ndef = ndefbuilder.interpret;
			//ndef.set(\inbus, inBus);
		};

	}

	// set individual fx stage parameters
	// this only works once the ndef has been built..
	//   Note: paramValue is set directly on the compiled ndef,
	//         so paramValue can also be a nodeproxy
	set {|stageName, paramName, paramValue|
		if(ndef.notNil) {
			var ndefParamName = "%_%".format(stageName, paramName).asSymbol;
			var currParamVal = ndef.get(ndefParamName);
			if(currParamVal.notNil) {
				ndef.set(ndefParamName, paramValue);
				if(currParamVal.isKindOf(NodeProxy)) {
					// if the param is currently a NodeProxy, be sure to free it..
					if(FX.verbose) {
						"Replacing NodeProxy '%' with static value".format(currParamVal).warn;
					};
					currParamVal.clear;
				};
			} {
				"Parameter name '%' does not exist on synth for %".format(ndefParamName, ndefid).error;
			};
		} {
			"Cannot set FX parameters on % - build Ndef first!".format(this.id).throw;
		};
		^this;
	}

	// apply an arbitrary envelope to an individual fx stage parameter
	//   env is an envelope with time value between 0-1 (for dur to work properly)
	env {|stageName, paramName, env, dur|

		if(ndef.notNil) {
			var ndefParamName;
			if(paramName.isNil) {
				ndefParamName = stageName; // probably \inamp or \outamp
			} {
				ndefParamName = "%_%".format(stageName, paramName).asSymbol;
			};
			if(ndef.get(ndefParamName).notNil) {
				var mod_ndefid = "%_%".format(id, ndefParamName).asSymbol;
				var mod_ndef = Ndef(mod_ndefid, { EnvGen.ar(env, 1, 1.0, 0.0, dur) });
				ndef.set(ndefParamName, mod_ndef);
				if(FX.verbose) {
					"Compiled envelope '%': %".format(mod_ndefid, mod_ndef).warn;
				};
			} {
				"Parameter name '%' does not exist on FX Unit for %".format(ndefParamName, ndefid).error;
			};
		} {
			"Cannot ramp FX unit % - build Ndef first!".format(this.id).throw;
		};
		^this;
	}

	// TODO:
	// apply a lfo or other cyclical function to an individual fx stage parameter
	lfo {

	}

	// apply a linear ramp to individual fx stage parameters
	//  this only works once the ndef has been built
	line {|stageName, paramName, start=1.0, end=0.0, dur=5, curve='lin'|
		^this.env(stageName, paramName, Env([start, start, end], [0, 1], curve), dur);
	}

	// applies a simple volume fade to the end gain stage of the FX Unit
	//   identical to line(\outamp, nil, 1.0, 0.0, 10, 'lin')
	fade {|start=1.0, end=0.0, dur=10, curve='lin'|
		^this.line(\outamp, nil, start, end, dur, curve);
	}


	// set input mix pre-gain (first gain stage after bus input to fx unit)
	pre {|gain=1.0|
		if(ndef.notNil) {
			ndef.set('inamp', gain);
		} {
			"Cannot set FX parameters on % - build Ndef first!".format(this.id).throw;
		};

		^this;
	}

	// set output (fader) gain (end gain stage before fx unit goes to master)
	amp {|gain=1.0|
		if(ndef.notNil) {
			ndef.set('outamp', gain);
		} {
			"Cannot set FX parameters on % - build Ndef first!".format(this.id).throw;
		};

		^this;
	}


	// Free all resources used by this FX unit...
	freeUnit {
		if(inBus.notNil) { inBus.free };
		if(ndef.notNil) { ndef.clear };
	}

	// convenience alias for freeUnit
	xx {
		this.freeUnit();
	}

	// get the fx unit's input bus
	//   use as the destination bus for incoming signals
	bus {
		^inBus;
	}

}

