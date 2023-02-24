/*
RAVE modelmanager provides a flexible instrument
for controlling multiple RAVE models in live performance.

NOTE: At the moment only supports one RAVE synth at a time..
TODO: Expand to allow managing multiple RAVE models and morphing between them.
See: Jennifer Hsu, "it's morphin time"
*/


/*
Language abstraction of a RAVE model
*/
RAVEModel {
	var <>id, <>path, <>numLatents, <>gate, <>gateThresh, <>preload, <>latency=0.11;
	var <>synth; // active synth for this model
	var <>inbus, <>outbus; // input and output busses for model signalpath

	var <>parentManager;

	*new {|id, path, numLatents, gate, gateThresh, preload, latency|
		^super.new.init(id, path, numLatents, gate, gateThresh, preload, latency);
	}

	*newFromSpec {|id, rootpath, modelSpec|
		^super.new.initFromSpec(id, rootpath, modelSpec);
	}

	initFromSpec {|id, rootpath, modelspec|
		this.init(
			id: id,
			path: rootpath +/+ modelspec[\path],
			numLatents: modelspec[\numLatents].asInteger,
			gate: modelspec[\gate].asBoolean,
			gateThresh: modelspec[\gateThresh].asFloat,
			preload: modelspec[\preload].asBoolean,
			latency: modelspec[\latency].asFloat,
		);
	}

	init {|id, path, numLatents, gate, gateThresh, preload, latency|
		this.id=id;
		this.numLatents=numLatents;
		this.gate=gate;
		this.gateThresh=gateThresh;
		this.preload=preload;
		this.latency=latency;
		this.synth=nil;

		this.path=path.asAbsolutePath;
		if( PathName(this.path).isFile ) {
			postln("FOUND MODEL FILE FOR: %".format(id));
		} {
			throw("MISSING MODEL FILE FOR: % - not found at %".format(id, this.path));
		};
	}


	/*
	Instantiate a basic Encoder/Decoder synth for this model.
	The synth is a basic Encoder/Decoder synth
	*/
	makeSynth {|inbus, outbus, targetGroup, addAction|
		"Create RAVE % with % % % %".format(id, inbus, outbus, targetGroup, addAction).postln;
		if(synth.notNil) {
			warn("Synth already exists for RAVE model % - freeing old synth!".format(id));
			synth.free; synth=nil;
		};

		this.inbus=inbus;
		this.outbus=outbus;

		synth = {|encoderScale=1.0, pregain=1.0, gain=1.0| // create RAVE synth..
			var insig, outsig, z;
			var zscale = this.numLatents.collect{ |i| ("ls"++i).asSymbol.kr(1.0) };
			var zbias = this.numLatents.collect{ |i| ("lb"++i).asSymbol.kr(0.0) };
			insig = In.ar(inbus) * pregain;
			z = RAVEEncoder.new( this.path, this.numLatents, insig) * encoderScale;
			z = (z * zscale) + zbias;
			outsig = RAVEDecoder.new( this.path, z );
			if(this.gate) {
				outsig = Compander.ar(outsig, outsig, this.gateThresh, 2.0, 1.0, 0.01, 0.01);
			};
			outsig * gain;
		}.play(target: targetGroup, outbus: outbus, addAction: addAction);
		^synth;
	}

	clearSynth {
		if(synth.notNil) { synth.free; synth=nil; };
	}

	setGain {|pregain, gain, encoderScale|
		var setargs=List.new;
		if(pregain.notNil) { setargs.add(\pregain); setargs.add(pregain) };
		if(gain.notNil) { setargs.add(\gain); setargs.add(gain) };
		if(encoderScale.notNil) { setargs.add(\encoderScale); setargs.add(encoderScale) };
		synth.set(*setargs);
		^this
	}

	// Set arbitrary name,value pairs on this model's synth, if active
	setParam {|... args|
		var res = synth;
		if(res.notNil) {
			postln("Set ((%)) on model % synth %".format(args, id, res));
			res.set(*args);
		} {
			error("This model % has no active synth!".format(id));
		};
		^res;
	}

	// Set latent bias values
	// latents is an array of values: if latents=nil then zero all biases for current model..
	bias {|latents|
		var synthargs, idx;
		if(latents.isNil) {
			latents = 0.0.dup(this.numLatents); // all latents
		};
		synthargs = Array.newClear(latents.size * 2);
		idx=0;
		latents.do {|lat, latnum|
			synthargs[idx] = ("lb"++latnum).asSymbol;
			synthargs[idx+1] = lat;
			idx = idx+2;
		};
		this.setParam(*synthargs);
		^this;
	}

	// Set latent scale values
	// if latents=nil then all biases for current model get set to unity
	scale {|latents|
		var synthargs, idx;
		if(latents.isNil) {
			latents = 0.0.dup(this.numLatents); // all latents
		};
		synthargs = Array.newClear(latents.size * 2);
		idx=0;
		latents.do {|lat, latnum|
			synthargs[idx] = ("ls"++latnum).asSymbol;
			synthargs[idx+1] = lat;
			idx = idx+2;
		};
		this.setParam(*synthargs);
		^this;
	}

}


RAVEModelManager {

	var <server;
	var <ins, <outs; // input and output busses, used to pipe audio to and from the RAVE models
	var <targetGroup; // where RAVE synths will go, should be after input UGens and before FX/master

	// Target for latent controls...
	var <defaultModel = nil; // currently selected model

	var <>mscale = 1.0; // scaling factor for latent biases, values >1.0 start to saturate
	var <availableModels; // all available RAVE models
	var <>mixer;

	var <>modelsRoot;
	var <models;
	var <synths; // active RAVE synths


	*new {|serv, target|
		^super.new.init(serv, target);
	}

	init {|serv, target|
		if(serv.isNil) {
			serv = Server.default;
		};
		server = serv;

		if(server.serverRunning.not) {
			"Cannot initialize RAVEModelManager - boot server first!".throw;
		};

		if(target.isNil) {
			target = Group.new(server, \addAfter);
		};
		targetGroup = target;
		ins = 3.collect {|idx| Bus.audio(server, 2) };
		outs = 3.collect {|idx| Bus.audio(server, 2) };
	}

	/*
	Creates a mixer ndef for creating a mix between rave inputs and outputs
	the mixer can then be inserted somewhere, for example, in an fx chain, in the doneCallback function..

	It can then be controlled either using a gui or commands...
	r.mixer.gui // via gui
	r.mixer.set(\i0pan, 0.0, \i0gain, 0.0, \o0pan, 0.0, \o0gain, 2.0) // via commands

	*/
	createMixer {|doneCallback|
		// the mixer will have a number of iXpan, iXgain, oXpan and oXgain parameters for controlling the mixer
		{
			mixer = Ndef(\raveModelMixer, {
				var raveins, raveouts, mix;
				raveins = ins.collect {|inbus, idx|
					Pan2.ar(
						DelayN.ar(
							In.ar(inbus, 2).sum,
							maxdelaytime: "i%maxdelay".format(idx).asSymbol.kr(1.0),
							delaytime: "i%delay".format(idx).asSymbol.kr(0.0)
						),
						"i%pan".format(idx).asSymbol.kr(0.0),
						"i%gain".format(idx).asSymbol.kr(1.0)
					)
				};

				raveouts = outs.collect {|outbus, idx|
					Pan2.ar(
						In.ar(outbus, 2).sum,
						"o%pan".format(idx).asSymbol.kr(0.0),
						"o%gain".format(idx).asSymbol.kr(1.0)
					)
				};

				mix = Mix(raveins) + Mix(raveouts);
				mix;
			});

			server.sync;
			mixer.asGroup.moveAfter(targetGroup);

			if(doneCallback.notNil) {
				doneCallback.value(mixer);
			}

		}.fork(AppClock);
	}

	/*
	Parse a modelspec Dictionary that contains information about RAVE torchscript models

	rootpath <str> root path to RAVE models
	modelspec <Dictionary> dictionary of model id->modelspec
	jsonfile <filepath> alternatively 'root' and 'models' can be specified in a json file

	Each modelspec entry must have the following parameters:
	  path  <str> path to model file, minus the rootpath
	  numLatents  <int> number of latents in the ts file
	  gate  <bool> add a noisegate or not
	  gateThresh <float> volume thresh for the gate to kick in
	  preload <bool> if true, preload the RAVE model
	*/
	parseModels {|rootpath, modelspec, jsonfile|
		var modelerror = false;

		if(jsonfile.notNil) { // Parse JSON
			var json = jsonfile.parseJSONFile;
			modelsRoot = json["root"];
			modelspec = Dictionary.new;
			json["models"].keysValuesDo {|id,jsonspec|
				var parsed;
				id=id.asSymbol;
				parsed = (
					id: id,
					path: jsonspec[\path],
					numLatents: jsonspec[\numLatents].asInteger,
					gate: jsonspec[\gate].interpret,
					gateThresh: jsonspec[\gateThresh].asFloat,
					preload: jsonspec[\preload].asBoolean,
				);
				modelspec[id]=parsed;
			};
		} { // Use input arguments
			modelsRoot = rootpath;
		};

		models = Dictionary.new;
		modelspec.keysValuesDo {|id, modelspec|
			id=id.asSymbol;
			models[id] = RAVEModel.newFromSpec(id, modelsRoot, modelspec);
			models[id].parentManager = this;
		};
	}

	/*
	Pre-load RAVE models
	Run this after parsing models to pre-load all models, caching them
	so that switching between models does not cause any glitches due to
	loading from disk.

	Any models that are preloaded by have existing synths have their synths freed... so only call this
	at the beginning of a patch.

	ids    a list of model ids to preload, if nil preloads all models
	*/
	preloadModels {|ids=nil, onComplete=nil|
		if(ids.isNil) { ids = models.keys };
		{
			var current_id=nil;
			if(defaultModel.notNil) {
				current_id = defaultModel.id;
			};
			ids.do {|model_id|
				var tmpsynth, model;
				model = models[model_id];
				if(model.notNil) {
					postln("\nPreloading Model: % ...".format(model_id));
					this.activateModel(model_id);
					2.wait;
					this.deactivateModel(model_id);
					2.wait;
				} {
					error("Could not find model with id %".format(model_id));
				};
			};

			// Finally, restore the original default model if there is one..
			if(current_id.notNil) {
				this.activateModel(current_id, makeDefault: true);
			};

			if(onComplete.notNil) {
				onComplete.value(this);
			};
		}.fork(AppClock);
	}

	// Set bias values on target model..
	// latents is an array of values: if latents=nil then zero all biases for current model..
	// id is a model id to target, if nil then defaultModel is targetted
	bias {|latents, id|
		var model;
		if(id.isNil) { model = defaultModel } { model = models[id] };
		if(model.notNil) {
			model.bias(latents);
		} {
			error("No active model >%< exists - activate a model?");
		};
	}

	// Set scale values on current model..
	// if latents=nil then all biases for current model get set to unity
	// id is a model id to target, if nil then defaultModel is targetted
	scale {|latents, id|
		var model;
		if(id.isNil) { model = defaultModel } { model = models[id] };
		if(model.notNil) {
			model.scale(latents);
		} {
			error("No active model >%< exists - activate a model?");
		};
	}

	// Set arbitrary name,value pairs on the default model's synth, if active
	setParam {|... args|
		var model = defaultModel;
		^defaultModel.setParam(*args);
	}

	// Set gain, pregain, and encoder scale on default model, if active
	setGain {|pregain, gain, encoderScale|
		^defaultModel.setGain(pregain, gain, encoderScale);
	}


	/*
	Set model mixer params.
	shortcut for mixer.set(...)
	*/
	mix {|... args|
		^mixer.set(*args);
	}


	listModels {
		models.keys.postln;
		^models.keys;
	}

	/*
	Print detailed list of model specs
	*/
	dumpModels {|id|
		if(id.isNil) {
			models.keysValuesDo {|key, val|
				"%: %".format(key, val).postln;
			};
		} {
			"%: %".format(id, models[id]).postln;
		}
	}

	/*
	Get a model by id.
	If id is nil, get the default model.
	*/
	getModel {|id=nil|
		var model = models[id];
		if(model.isNil) { model = defaultModel };
		if(model.isNil) {
			error("No models available! Maybe activate one first?");
		}
		^model;
	}

	// Shortcut for getModel
	at {|id|
		^this.getModel(id);
	}

	/*
	Creates a RAVE synth from a given model
	by default this becomes the target model
	Note: usually you don't call this method directly, but instead
	use setModel or other convenience methods..

	id            model id
	inbus         audio bus to use as input to the RAVE synth, if nil use this.ins[0]
	outbus        audio bus to use as output from the RAVE synth, if nil use this.outs[0]
	target        server group to put the RAVE synth, if nil use default targetGroup
	makeDefault   if true, make this model the default target model
	*/
	activateModel {|id, inbus, outbus, target, makeDefault=true|
		var res=nil, model = models[id];
		if(inbus.isNil) { inbus = ins[0] };
		if(outbus.isNil) { outbus = outs[0] };
		if(target.isNil) { target = targetGroup };
		if(model.notNil) {
			res = model.synth;
			if(makeDefault) {
				defaultModel = model;
			};

			// For now just remake the synth every time...
			res = model.makeSynth(inbus:inbus, outbus:outbus, target:target, addAction:\addToTail);

			/*
			TODO: If inbus/outbus change we want to modify the synth...
			better to make inbus/outbus modifiable parameters
			if(res.isNil) {
				res = model.makeSynth(inbus:inbus, outbus:outbus, target:target, addAction:\addToTail);
			} {
				warn("Model % is already active with synth: %".format(id, model.synth));
			};
			*/
		} {
			error("Unknown model '%'".format(id));
		};
		^res;
	}

	deactivateModel {|id|
		var model = models[id];
		if(model.notNil) {
			if(model.synth.notNil) {
				postln("Freeing model % - synth %".format(id, model.synth));
				model.clearSynth();
			} {
				warn("Model % is not active, no synth exists".format(id));
			};
		} {
			error("Unknown model '%'".format(id));
		};
	}

	/*
	Puts model with id on channel chan.
	Only one instance of a single model can exist on a single channel set
	id - the model
	chan - the in/out channel to route in/out of the model
	clearChan - if true, deactivates any models that are currently assigned to chan
	targetGroup - see activateModel
	makeDefault - see activateModel
	*/
	setModel {|id, chan=0, clearChan=true, targetGroup=nil, makeDefault=true|
		var inbus = ins[chan], outbus = outs[chan];
		var model = models[id];
		postln("Set model %, using in:% out:%".format(id, inbus, outbus));
		if(model.notNil) {
			if(clearChan) { this.clearChannel(chan) };
			// Set latency
			mixer.set("i%delay".format(chan).asSymbol, model.latency);
			this.activateModel(
				id:id,
				inbus:inbus,
				outbus:outbus,
				target:targetGroup,
				makeDefault:makeDefault
			);
		} {
			error("MODEL % does not exist".format(id));
		};
		^model;
	}

	/*
	Clears any RAVE models on a given channel
	*/
	clearChannel {|chan|
		var inbus = ins[chan], outbus = outs[chan];
		postln("Clear all models on input channel %".format(chan));
		models.do {|mod|
			if((mod.inbus.notNil).and { mod.inbus.index == inbus.index } ) {
				postln("Deactivate %".format(mod.id));
				this.deactivateModel(mod.id);
			};
		};
	}

	/*
	Make an active model the default.
	*/
	selectDefaultModel {|id|
		var model = models[id];
		if(model.notNil) {
			var isactive = if( model.synth.isNil, { "not active" }, { "active" });
			postln("Making model % default: model is %".format(id,isactive));
			defaultModel = model;
		} {
			error("No model with id %".format(id));
		}
		^model;
	}

	// Clear all RAVE synths and clean up resources...
	clearAll {
		models.keysValuesDo {|id,model|
			model.clearSynth();
		};
		defaultModel=nil;
	}
}



