frontend_dev:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp browserify --main MainDev --to dist/MainDev.js

watch_frontend_dev:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp --watch browserify --main MainDev --to dist/MainDev.js



frontend_prod:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp browserify --main Main --to dist/Main.js

watch_frontend_prod:
	cd BannoDemo-frontend && \
	npm install  && \
	bower install && \
	pulp build && \
	pulp --watch browserify --main Main --to dist/Main.js
