frontend_dev:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp browserify --main MainDev --to static/MainDev.js

watch_frontend_dev:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp --watch browserify --main MainDev --to static/MainDev.js



frontend_prod:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp browserify --main Main --to static/Main.js

watch_frontend_prod:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp --watch browserify --main Main --to static/Main.js

clean:
	rm -rf target && \
	cd BannoDemo-frontend && \
	rm -rf bower_components node_modules output package-lock.json
