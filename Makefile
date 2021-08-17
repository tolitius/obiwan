.PHONY: clean jar outdated install deploy tree repl

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

repl:
	clojure -M:dev
