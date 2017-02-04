# TodoMVC done with re-frame

[bones.editable](https://github.com/teaforthecat/bones-editable) implementation of [TodoMVC](http://todomvc.com/).

This project was forked/extracted
from [re-frame](https://github.com/Day8/re-frame) and then
had [bones.editable](https://github.com/teaforthecat/bones-editable) put on top.

## Setup And Run

1. Install [Leiningen](http://leiningen.org/)  (plus Java).

2. Get the project

   ```
   git clone https://github.com/teaforthecat/bones-todomvc.git
   ```

3. Clean build
   ```
   lein do clean, figwheel
   ```

5. Run
   You'll have to wait for step 4 to do its compile, but then:
   ```
   open http://localhost:3450
   ```


## Compile an optimized version

1. Compile
   ```
   lein do clean, with-profile prod compile
   ```

2. Open the following in your browser
   ```
   resources/public/index.html
   ```


