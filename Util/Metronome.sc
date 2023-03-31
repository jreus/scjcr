/*_____________________________________________________________
Metronome
Simple metronome synced to a clocks

(C) 2019 Jonathan Reus
http://jonathanreus.com

This software is licensed under the Gnu Public License v3.
read the full license at: http://www.gnu.org/licenses/

________________________________________________________________*/



/*
@usage

Metronome.start(TempoClock, beats);
Metronome.stop;

*/


/*
@usage
s.boot;
Metronome.start(TempoClock, pan: -1);
Metronome.start(TempoClock, pan: 0);
Metronome.stop;

*/

Metronome {
  classvar <task, <clickPattern;

  *start {|clock, pattern="ABBB", out=0, amp=1.0, pan=1|
    clickPattern = pattern.toUpper;
    this.pr_addSynthDefs();
    if(task.notNil) { task.stop; task = nil };
    task = Task {
      var beat, beats = clickPattern.size;
      inf.do {|i|
        beat = (i % beats);
        if(clickPattern[beat] == $A) {
          (instrument: \tick, freq: 2000, out: out, amp: amp, pan: pan).play;
        };
        if(clickPattern[beat] == $B) {
          (instrument: \tick, freq: 1000, out: out, amp: amp, pan: pan).play;
        };
        if(clickPattern[beat] == $C) {
          (instrument: \tick, freq: 500, out: out, amp: amp, pan: pan).play;
        };
        1.0.yield;
      };
    };
    task.play(clock, quant: 1);
  }

  *stop {
    if(task.notNil) { task.stop; task = nil };
  }

  *pr_addSynthDefs {
    SynthDef(\tick, {|out=0, pan=1, freq=2000, amp=1.0|
      var sig = Resonz.ar(Impulse.ar(0, mul: 100), freq, 0.03, mul: 20);
      sig = sig * EnvGen.ar(Env.perc(0,0.4), timeScale: 1, doneAction: 2);
      Out.ar(out, Pan2.ar(sig, pan, amp));
    }).add
  }

}



