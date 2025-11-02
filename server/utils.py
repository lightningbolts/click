import requests

def get_file_name(file_path):
    return file_path.split('/')[-1]

def get_semantic_location(geo_location:tuple[float, float]) -> object:
    # For example, 47.655459, -122.307183 becomes "University of Washington Seattle Research Commons"
    # Use OpenStreetMaps + Nominatim API to reverse geocode
    url = f"https://nominatim.openstreetmap.org/reverse?lat={geo_location[0]}&lon={geo_location[1]}&format=json"
    response = requests.get(url)
    data = response.json()
    return data

def print_response(response):
    print(response.status_code, response.reason)
    print(response.text)

