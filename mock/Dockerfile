FROM python:3.12-alpine
WORKDIR /app
COPY server.py openapi.json ./
COPY static ./static
RUN addgroup -g 1000 mock && adduser -D -u 1000 -G mock mock && mkdir /data && chown mock:mock /data
USER mock
EXPOSE 8090
CMD ["python", "server.py"]
