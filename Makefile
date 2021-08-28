.PHONY: clean jar outdated install deploy tree test repl

clean:
	clojure -Sforce -T:build clean

jar:
	clojure -T:build jar

outdated:
	clojure -M:outdated

install: jar
	clojure -T:build install

deploy: jar
	clojure -T:build deploy

tree:
	clojure -Xdeps tree

test:
	clojure -X:test :patterns '[".*test"]'

## does not work with "-M"s ¯\_(ツ)_/¯
repl:
	clojure -A:dev -A:test -A:repl
