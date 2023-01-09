/*
RAVE modelmanager provides a flexible instrument
for controlling multiple RAVE models in live performance.

NOTE: At the moment only supports one RAVE synth at a time..
TODO: Expand to allow managing multiple RAVE models and morphing between them.
See: Jennifer Hsu, "it's morphin time"
*/

RAVEModelManager {

	var <server;
	var <ins, <outs; // input and output busses, used to pipe audio to and from the RAVE models
	var <targetGroup; // group where RAVE synths will sit, ideally this should go after input UGens and before FX and master synths
	var <targetModel = nil; // currently selected model
	var <targetModelSynth = nil; // currently selected RAVE synth, all control operations target this synth
	var <>mscale = 1.0; // scaling factor for latent biases, values >1.0 start to saturate
	var <availableModels; // all available RAVE models
	var <mixer;

	var <>modelsRoot;
	var <models;


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
					Pan2.ar(In.ar(inbus, 2).sum, "i%pan".format(idx).asSymbol.kr(0.0), "i%gain".format(idx).asSymbol.kr(1.0))
				};

				raveouts = outs.collect {|outbus, idx|
					Pan2.ar(In.ar(outbus, 2).sum, "o%pan".format(idx).asSymbol.kr(0.0), "o%gain".format(idx).asSymbol.kr(1.0))
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
	*/
	parseModels {|rootpath, modelspec, jsonfile|
		var modelerror = false;

		if(jsonfile.notNil) { // Parse JSON
			var json = jsonfile.parseJSONFile;
			modelsRoot = json["root"];
			models = Dictionary.new;
			json["models"].keysValuesDo {|id, modelspec|
				models[id.asSymbol] = (
					path: modelspec["path"],
					numLatents: modelspec["numLatents"].asInteger,
					gate: modelspec["gate"].interpret,
					gateThresh: modelspec["gateThresh"].asFloat
				);
			};
		} { // Use input arguments
			modelsRoot = rootpath;
			models = modelspec;
		};

		"RAVEModelManager. ...scanning RAVE models...".postln;
		models.keysValuesDo {|id, path|
			if( this.getModelPath(id).isFile ) {
				"FOUND MODEL: %".format(id).postln;
			} {
				"COULD NOT FIND MODEL FILE: %".format(id).error;
				modelerror=true;
			};
		};

		if(modelerror) {
			"One or more RAVE models could not be found!".throw;
		};

		"RAVEModelManager. ...generating synth functions for all models...".postln;
		models.keys.do {|id|
			var model = models[id];
			"Parsing % ...".format(id, model).postln;
			// Add synth param and makeSynth function
			model[\synth] = nil;
			model[\makeSynth] = {|inbus, outbus, targetGroup, addAction|
				"Create RAVE % with % % % %".format(id, inbus, outbus, targetGroup, addAction).postln;
				model[\synth].free; // free preexisting synth
				//"Making RAVE for model %".format(model).postln;
				model[\synth] = {|encoder=1.0, gain=1.0| // create RAVE synth..
					var insig, outsig, z;
					var zscale = model[\numLatents].collect{ |i| ("ls"++i).asSymbol.kr(1.0) };
					var zbias = model[\numLatents].collect{ |i| ("lb"++i).asSymbol.kr(0.0) };
					insig = In.ar(inbus);
					z = RAVEEncoder.new( this.getRawModelPath(id), model[\numLatents], insig) * encoder;
					z = (z * zscale) + zbias;
					outsig = RAVEDecoder.new( this.getRawModelPath(id), z );
					if(model[\gate]) {
						outsig = Compander.ar(outsig, outsig, model[\gateThresh], 2.0, 1.0, 0.01, 0.01);
					};
					outsig * gain;
				}.play(target: targetGroup, outbus: outbus, addAction: addAction);
				//"Made RAVE".postln;
				model[\synth];
			};
		};

	}

	// Get the path to a given model as a PathName
	getModelPath {|id|
		^PathName( modelsRoot +/+ models[id].path )
	}

	// Get the path to a given model as a pathstring
	getRawModelPath {|id|
		^this.getModelPath(id).fullPath
	}

	// Set bias values on current model..
	// if latents=nil then zero all biases for current model..
	bias {|latents|
		if(targetModel.notNil) {
			var synthargs, idx;
			if(latents.isNil) {
				latents = 0.0.dup(targetModel['numLatents']); // all latents
			};
			synthargs = Array.newClear(latents.size * 2);
			idx=0;
			latents.do {|lat, latnum|
				synthargs[idx] = ("lb"++latnum).asSymbol;
				synthargs[idx+1] = lat;
				idx = idx+2;
			};

			this.setParam(*synthargs);
		};
	}

	// Set scale values on current model..
	// if latents=nil then all biases for current model get set to unity
	scale {|latents|
		if(targetModel.notNil) {
			var synthargs, idx;
			if(latents.isNil) {
				latents = 0.0.dup(targetModel['numLatents']); // all latents
			};
			synthargs = Array.newClear(latents.size * 2);
			idx=0;
			latents.do {|lat, latnum|
				synthargs[idx] = ("ls"++latnum).asSymbol;
				synthargs[idx+1] = lat;
				idx = idx+2;
			};

			this.setParam(*synthargs);
		};
	}


	// Set arbitrary name,value pairs on the currently selected RAVE synth
	setParam {|... args|
		postln("Set ((%)) on %".format(args, targetModelSynth));
		targetModelSynth.set(*args);
		^targetModelSynth;
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

	currentModel {
		if(targetModel.notNil) {
			"%: %".format(targetModel['id'], targetModel).postln;
		} {
			"Cannot get current model, select a model first using selectModel!".error;
		}
	}

	/*
	Activates a model and makes it the target of all control operations.
	id            model id
	inbus         audio bus to use as input to the RAVE synth, if nil us ins[0]
	outbus        audio bus to use as output from the RAVE synth, if nil use outs[0]
	target        server group to put the RAVE synth, if nil use default targetGroup
	*/
	selectModel {|id, inbus, outbus, target|
		var model = models[id];
		if(inbus.isNil) { inbus = ins[0] };
		if(outbus.isNil) { outbus = outs[0] };
		if(model.notNil) {
			"Selecting Model: %".format(id).postln;
			model['id'] = id;
			targetModel = model;
			targetModelSynth.free; // TODO: Maybe I don't want to do this? Should I allow multiple RAVE synths simultaneously?
			if(target.isNil) { target = targetGroup };
			model[\makeSynth].value(inbus, outbus, target, \addToTail);
			targetModelSynth = model[\synth];
		} {
			"Unknown model '%'".format(id).warn;
		};
	}

	// Clear all RAVE synths and clean up resources...
	clearAll {
		// For now it's just freeing the target synth..
		targetModelSynth.free;
	}

}



