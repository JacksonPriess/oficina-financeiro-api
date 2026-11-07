FROM python:3.12-alpine
WORKDIR /app
COPY server.py openapi.json ./
COPY static ./static
RUN adduser -D mock && mkdir /data && chown mock /data
USER mock
EXPOSE 8090
CMD ["python", "server.py"]
