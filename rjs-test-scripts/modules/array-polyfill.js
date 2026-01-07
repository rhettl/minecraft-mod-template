import {random} from "./random.js";

if (!Array.prototype.random) {
  Array.prototype.random = function () {
    return this[random(this.length)];
  }
}
if (!Array.prototype.randomIndex) {
  Array.prototype.randomIndex = function () {
    if (this.length === 0) return -1;
    return random(this.length);
  }
}
if (!Array.prototype.randomSet) {
  Array.prototype.randomSet = function (count) {
    if (count <= 0) {
      return [];
    }
    if (count > this.length) {
      return [...this];
    }

    let indexes = new Set();
    while (indexes.size < count) {
      let nextRandom = random(this.length);
      if (indexes.has(nextRandom)) {
        continue;
      }
      indexes.add(nextRandom);
    }
    return this.filter((item, i) => indexes.has(i));
  }
}
if (!Array.prototype.shuffle) {
  Array.prototype.shuffle = function () {
    let arr = [...this];
    for (let i = arr.length-1; i > 0; i--) {
      let j = random(i+1);
      let tmp = arr[i];
      arr[i] = arr[j];
      arr[j] = tmp;
    }
    return arr;
  }
}



// exports as side effects
export {};